package com.pickme.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 새 비밀번호는 BCrypt 로 저장하고, 기존 Flask(Werkzeug) 회원의 해시도 검증합니다.
 *
 * <p>Werkzeug 형식: {@code scrypt:N:r:p$salt$hex} 또는 {@code pbkdf2:sha256:iterations$salt$hex}.
 * 기존 회원은 비밀번호를 바꾸면 BCrypt 로 전환됩니다.
 */
public class LegacyAwarePasswordEncoder implements PasswordEncoder {

    private static final int WERKZEUG_DEFAULT_PBKDF2_ITERATIONS = 600_000;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        if (encodedPassword.startsWith("scrypt:") || encodedPassword.startsWith("pbkdf2:")) {
            return matchesWerkzeug(rawPassword.toString(), encodedPassword);
        }
        try {
            return bcrypt.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean matchesWerkzeug(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$", 3);
            if (parts.length != 3) {
                return false;
            }
            String[] method = parts[0].split(":");
            byte[] salt = parts[1].getBytes(StandardCharsets.UTF_8);
            byte[] expected = HexFormat.of().parseHex(parts[2]);
            byte[] pw = password.getBytes(StandardCharsets.UTF_8);
            byte[] actual;

            if ("scrypt".equals(method[0])) {
                int n = method.length > 1 ? Integer.parseInt(method[1]) : 32768;
                int r = method.length > 2 ? Integer.parseInt(method[2]) : 8;
                int p = method.length > 3 ? Integer.parseInt(method[3]) : 1;
                actual = SCrypt.generate(pw, salt, n, r, p, expected.length);
            } else if ("pbkdf2".equals(method[0])) {
                String hash = method.length > 1 ? method[1] : "sha256";
                int iterations = method.length > 2
                        ? Integer.parseInt(method[2]) : WERKZEUG_DEFAULT_PBKDF2_ITERATIONS;
                String algorithm = switch (hash) {
                    case "sha256" -> "PBKDF2WithHmacSHA256";
                    case "sha512" -> "PBKDF2WithHmacSHA512";
                    case "sha1" -> "PBKDF2WithHmacSHA1";
                    default -> null;
                };
                if (algorithm == null) {
                    return false;
                }
                KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expected.length * 8);
                actual = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
            } else {
                return false;
            }
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }
}
