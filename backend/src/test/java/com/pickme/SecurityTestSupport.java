package com.pickme;

import com.pickme.member.User;
import com.pickme.member.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"pickme.admin-username=security-test-admin", "pickme.admin-password-hash=$2y$10$7y.JmRE826ultFbuPJlLdebP3HLAtRaxn2Gnj2mcZDYDEWt4iysV.", "pickme.cookie-secure=false", "spring.datasource.url=jdbc:h2:mem:pickme-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.data.redis.url=${TEST_REDIS_URL:redis://localhost:6379/15}"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class SecurityTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StringRedisTemplate redis;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    static final String ADMIN = "security-test-admin", PASSWORD = "security-test-password";
    private final Set<String> sessions = new HashSet<>();
    private final List<Integer> members = new ArrayList<>();
    record Login(String token, Cookie cookie) {}
    Login readLogin(MvcResult result) throws Exception {
        String token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
        String header = result.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("PICKME_REFRESH=")).findFirst().orElseThrow();
        String value = header.split(";",2)[0].substring("PICKME_REFRESH=".length());
        sessions.add("pickme:auth:session:" + value.split("\\.")[0]);
        return new Login(token,new Cookie("PICKME_REFRESH",value));
    }
    Login adminLogin() throws Exception {
        return readLogin(mvc.perform(post("/api/admin/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.41");return r;}).contentType("application/json").content(json.writeValueAsString(Map.of("username",ADMIN,"password",PASSWORD)))).andExpect(status().isOk()).andReturn());
    }
    Login memberLogin() throws Exception {
        String suffix=UUID.randomUUID().toString();
        String email=suffix+"@example.test";
        User user=users.saveAndFlush(new User(email,suffix,encoder.encode(PASSWORD)));
        members.add(user.getId());
        return readLogin(mvc.perform(post("/api/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.42");return r;}).contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn());
    }
    Login refresh(Login login) throws Exception {
        return readLogin(mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(login.cookie())).andExpect(status().isOk()).andReturn());
    }
    @AfterEach void cleanupSecurityFixtures() {
        for(String key:sessions) redis.delete(key);
        // Remove only the dedicated test rate-limit keys.
        for(String ip:List.of("192.0.2.41","192.0.2.42")) {
            try { redis.delete("pickme:auth:ip:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ip.getBytes(java.nio.charset.StandardCharsets.UTF_8)))); } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        }
        for(Integer id:members) users.deleteById(id);
    }
}
