package com.pickme.member;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.pickme.common.ApiException;

@Component
public class AdminAccess {

    public record Principal(String username) implements Serializable {
    }

    private final String username;
    private final String passwordHash;
    private final PasswordEncoder encoder;

    public AdminAccess(
            @Value("${pickme.admin-username:admin}") String username,
            @Value("${pickme.admin-password-hash:}") String passwordHash,
            PasswordEncoder encoder) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.encoder = encoder;
    }

    String configuredName() { return username; }
    String fingerprint() { return TokenService.hash(passwordHash); }

    public boolean available() {
        return !username.isBlank() && !passwordHash.isBlank();
    }

    public Principal authenticate(String name, String password) {
        if (!available()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "관리자 계정 설정이 필요합니다.");
        }

        boolean valid = password != null
                && password.length() <= 1024
                && encoder.matches(password, passwordHash);

        if (!valid || !username.equals(name)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "관리자 아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return new Principal(username);
    }

    public static boolean current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        return auth != null
                && auth.getPrincipal() instanceof Principal
                && auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public static String currentName() {
        if (!current()) {
            return null;
        }

        return ((Principal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).username();
    }
}
