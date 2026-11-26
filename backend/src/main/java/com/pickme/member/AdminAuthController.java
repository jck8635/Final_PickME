package com.pickme.member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAccess admins;
    private final TokenService tokens;

    public AdminAuthController(AdminAccess admins, TokenService tokens) {
        this.admins = admins; this.tokens=tokens;
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

        tokens.limit("admin",req.username(),request.getRemoteAddr());
        var principal=admins.authenticate(req.username(),req.password());
        tokens.clearLimit("admin",req.username());
        return Map.of("username",principal.username(),"accessToken",tokens.issue(tokens.admin(principal.username()),request,response));
    }
    @PostMapping("/logout")
    public Map<String,String> logout(HttpServletRequest request,HttpServletResponse response){tokens.logout(request,response);return Map.of("message","관리자 로그아웃되었습니다.");}
}
