package com.pickme.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import com.pickme.common.ApiException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final String COOKIE = "PICKME_REFRESH";
    private static final long ACCESS = 900, REFRESH = 604800;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final AdminAccess admins;
    private final byte[] secret;
    private final boolean secure;
    public record Identity(String subject, String role, String fingerprint) {}
    public record Verified(String sid, Identity identity) {}
    public TokenService(StringRedisTemplate redis, UserRepository users, AdminAccess admins,
            @Value("${pickme.jwt-secret:}") String key,
            @Value("${pickme.cookie-secure:false}") boolean secure,
            org.springframework.core.env.Environment environment) {
        this.redis=redis; this.users=users; this.admins=admins; this.secure=secure;
        if (key.isBlank() && environment.matchesProfiles("test")) key= random();
        if (key.isBlank()) throw new IllegalStateException("JWT_SECRET must be configured");
        secret=Base64.getUrlDecoder().decode(key);
        if(secret.length<32) throw new IllegalStateException("JWT_SECRET requires 32 random bytes");
        if(environment.matchesProfiles("prod") && !secure) throw new IllegalStateException("Production requires secure cookies");
    }
    static String random() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static String hash(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);} }
    static ApiException unauthorized(){return new ApiException(HttpStatus.UNAUTHORIZED,"인증이 만료되었습니다. 다시 로그인해주세요.");}
    private String key(String sid){return "pickme:auth:session:"+sid;}
    public Identity member(User user){return new Identity(user.getId().toString(),"USER",hash(user.getPasswordHash()));}
    public Identity admin(String name){return new Identity(name,"ADMIN",admins.fingerprint());}
    private boolean current(Identity i){
        if("ADMIN".equals(i.role())) return admins.available() && admins.configuredName().equals(i.subject()) && admins.fingerprint().equals(i.fingerprint());
        if(!"USER".equals(i.role())) return false;
        try{return users.findById(Integer.valueOf(i.subject())).map(u->hash(u.getPasswordHash()).equals(i.fingerprint())).orElse(false);}catch(NumberFormatException e){return false;}
    }
    private Identity identity(Map<Object,Object> m){if(m.isEmpty()) throw unauthorized(); return new Identity((String)m.get("sub"),(String)m.get("role"),(String)m.get("fp"));}
    private String jwt(String sid, Identity i){
        try {
            Instant now=Instant.now();
            var claims=new JWTClaimsSet.Builder().issuer("pickme").audience("pickme-api").subject(i.subject()).claim("role",i.role()).claim("sid",sid).jwtID(UUID.randomUUID().toString()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(ACCESS))).build();
            var token=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims); token.sign(new MACSigner(secret)); return token.serialize();
        } catch(JOSEException e){throw new IllegalStateException(e);}
    }
    public Verified verify(String raw){
        try {
            var token=SignedJWT.parse(raw);
            if(!JWSAlgorithm.HS256.equals(token.getHeader().getAlgorithm()) || !token.verify(new MACVerifier(secret))) throw unauthorized();
            var c=token.getJWTClaimsSet();
            if(!"pickme".equals(c.getIssuer()) || !c.getAudience().contains("pickme-api") || c.getExpirationTime()==null || !c.getExpirationTime().after(new Date())) throw unauthorized();
            String sid=c.getStringClaim("sid"); UUID.fromString(sid);
            Identity i=identity(redis.opsForHash().entries(key(sid)));
            if(!i.subject().equals(c.getSubject()) || !i.role().equals(c.getStringClaim("role")) || !current(i)) throw unauthorized();
            return new Verified(sid,i);
        } catch(java.text.ParseException | JOSEException | IllegalArgumentException e){throw unauthorized();}
    }
    private void cookie(HttpServletResponse res,String value,long age){res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,value).httpOnly(true).secure(secure).sameSite("Lax").path("/api").maxAge(age).build().toString());res.setHeader("Cache-Control","no-store");}
    private String[] read(HttpServletRequest req){
        if(req.getCookies()!=null) for(var c:req.getCookies()) if(COOKIE.equals(c.getName())){
            String[] a=c.getValue().split("\\.",-1); if(a.length==2 && a[0].matches("[0-9a-f-]{36}") && a[1].matches("[A-Za-z0-9_-]{43}")) return a;
        } return null;
    }
    private static final DefaultRedisScript<Long> CREATE=new DefaultRedisScript<>("redis.call('HSET',KEYS[1],'sub',ARGV[1],'role',ARGV[2],'fp',ARGV[3],'current',ARGV[4]); redis.call('EXPIRE',KEYS[1],ARGV[5]); return 1",Long.class);
    private static final DefaultRedisScript<Long> ROTATE=new DefaultRedisScript<>("""
        local old=redis.call('HGET',KEYS[1],'current')
        if not old then return 0 end
        if old~=ARGV[1] then
          if redis.call('HEXISTS',KEYS[1],'used:'..ARGV[1])==1 then redis.call('DEL',KEYS[1]); return -1 end
          return 0
        end
        redis.call('HSET',KEYS[1],'used:'..old,'1','current',ARGV[2]); return redis.call('TTL',KEYS[1])
        """,Long.class);
    private static final DefaultRedisScript<Long> REVOKE=new DefaultRedisScript<>("if redis.call('HGET',KEYS[1],'current')==ARGV[1] or redis.call('HEXISTS',KEYS[1],'used:'..ARGV[1])==1 then return redis.call('DEL',KEYS[1]) end return 0",Long.class);
    public String issue(Identity i,HttpServletRequest req,HttpServletResponse res){
        revoke(req); String sid=UUID.randomUUID().toString(), r=random();
        redis.execute(CREATE,List.of(key(sid)),i.subject(),i.role(),i.fingerprint(),hash(r),Long.toString(REFRESH));
        cookie(res,sid+"."+r,REFRESH); return jwt(sid,i);
    }
    public String refresh(HttpServletRequest req,HttpServletResponse res){
        String[] a=read(req); if(a==null) throw unauthorized();
        res.setHeader("Cache-Control","no-store");
        Identity i=identity(redis.opsForHash().entries(key(a[0]))); if(!current(i)) throw unauthorized();
        String next=random(); Long ttl=redis.execute(ROTATE,List.of(key(a[0])),hash(a[1]),hash(next));
        if(ttl==null || ttl<=0) throw unauthorized();
        cookie(res,a[0]+"."+next,ttl); return jwt(a[0],i);
    }
    private void revoke(HttpServletRequest req){String[] a=read(req);if(a!=null) redis.execute(REVOKE,List.of(key(a[0])),hash(a[1]));}
    public void logout(HttpServletRequest req,HttpServletResponse res){
        revoke(req); Object sid=req.getAttribute("pickme.sid"); if(sid instanceof String s) redis.delete(key(s)); cookie(res,"",0);
    }
    private static final DefaultRedisScript<Long> LIMIT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],900) end; return n",Long.class);
    public void limit(String kind,String account,String ip){
        Long a=redis.execute(LIMIT,List.of("pickme:auth:attempt:"+kind+":"+hash(Objects.toString(account,"").strip().toLowerCase(Locale.ROOT))));
        Long b=redis.execute(LIMIT,List.of("pickme:auth:ip:"+hash(ip)));
        if(a==null || b==null || a>5 || b>50) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"로그인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }
    public void clearLimit(String kind,String account){redis.delete("pickme:auth:attempt:"+kind+":"+hash(Objects.toString(account,"").strip().toLowerCase(Locale.ROOT)));}
}
