package com.pickme.member;
import java.io.IOException;
import java.util.List;
import com.pickme.common.ApiException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    public JwtAuthenticationFilter(TokenService tokens){this.tokens=tokens;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getServletPath();
        String h=req.getHeader("Authorization");
        if(h!=null && !path.equals("/api/auth/refresh") && !path.equals("/api/auth/login") && !path.equals("/api/admin/auth/login")){
            try {
                if(!h.startsWith("Bearer ") || h.length()>8192) throw TokenService.unauthorized();
                var v=tokens.verify(h.substring(7)); var i=v.identity();
                Object principal="ADMIN".equals(i.role())?new AdminAccess.Principal(i.subject()):new LoginUser(Integer.valueOf(i.subject()));
                var context=SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+i.role()))));
                SecurityContextHolder.setContext(context); req.setAttribute("pickme.sid",v.sid());
            }catch(ApiException e){res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"인증이 만료되었습니다.\"}");return;}
            catch(org.springframework.dao.DataAccessException e){res.setStatus(503);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"인증 저장소에 연결할 수 없습니다.\"}");return;}
        }
        chain.doFilter(req,res);
    }
}
