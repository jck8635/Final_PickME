package com.pickme.member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAccess admins;

    public AdminAuthController(AdminAccess admins) {
        this.admins = admins;
    }

    public record Credentials(String username, String password) {
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Map<String, Object> result = new HashMap<>();

        result.put(
                "admin",
                AdminAccess.current()
                        ? Map.of("username", AdminAccess.currentName())
                        : null);

        result.put("configured", admins.available());

        return result;
    }

    @PostMapping("/login")
    public Map<String, String> login(
            @RequestBody Credentials req,
            HttpServletRequest request,
            HttpServletResponse response) {

        var principal = admins.authenticate(req.username(), req.password());

        var old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }

        request.getSession(true);

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);

        SecurityContextHolder.setContext(context);

        new HttpSessionSecurityContextRepository()
                .saveContext(context, request, response);

        return Map.of("username", principal.username());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        var session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return Map.of("message", "관리자 로그아웃되었습니다.");
    }
}
