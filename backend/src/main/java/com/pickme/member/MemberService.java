package com.pickme.member;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.common.ApiException;

/** 기존 Flask 의 services/member_service.py 를 옮긴 것. 검증 규칙과 메시지를 그대로 유지합니다. */
@Service
public class MemberService {

    private static final Pattern EMAIL_LOCAL = Pattern.compile("[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final int MAX_PASSWORD = 1024;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public MemberService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String rawEmail, String rawNickname, String rawPassword) {
        String email = email(rawEmail);
        String nickname = nickname(rawNickname);
        String password = password(rawPassword);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("이미 가입된 이메일입니다.");
        }
        if (users.existsByNickname(nickname)) {
            throw ApiException.conflict("이미 사용 중인 닉네임입니다.");
        }
        try {
            return users.saveAndFlush(new User(email, nickname, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("이미 사용 중인 회원 정보입니다. 이메일과 닉네임을 확인해주세요.");
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(String rawEmail, String password) {
        String email = email(rawEmail);
        if (password == null || password.isEmpty()) {
            throw ApiException.badRequest("비밀번호를 입력해주세요.");
        }
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !matches(user, password)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User get(Integer userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    @Transactional
    public User changeNickname(Integer userId, String rawNickname) {
        User user = get(userId);
        String nickname = nickname(rawNickname);
        if (users.existsByNicknameAndIdNot(nickname, user.getId())) {
            throw ApiException.conflict("이미 사용 중인 닉네임입니다.");
        }
        user.setNickname(nickname);
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("이미 사용 중인 닉네임입니다.");
        }
        return user;
    }

    @Transactional
    public void changePassword(Integer userId, String current, String newPassword, String confirmation) {
        User user = get(userId);
        if (!matches(user, current)) {
            throw ApiException.badRequest("현재 비밀번호가 올바르지 않습니다.");
        }
        String validated = password(newPassword);
        if (!validated.equals(confirmation)) {
            throw ApiException.badRequest("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (matches(user, validated)) {
            throw ApiException.badRequest("현재 비밀번호와 다른 새 비밀번호를 입력해주세요.");
        }
        user.setPasswordHash(passwordEncoder.encode(validated));
    }

    private boolean matches(User user, String password) {
        if (password == null || password.isEmpty() || password.length() > MAX_PASSWORD) {
            return false;
        }
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    // ---------- 입력 검증 (member_service._email / _nickname / _password) ----------

    static String email(String value) {
        String email = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            throw ApiException.badRequest("이메일을 입력해주세요.");
        }
        if (email.length() > 255) {
            throw ApiException.badRequest("이메일은 255자 이내로 입력해주세요.");
        }
        if (email.chars().filter(c -> c == '@').count() != 1) {
            throw ApiException.badRequest("올바른 이메일 형식으로 입력해주세요.");
        }
        String[] parts = email.split("@", -1);
        String local = parts[0];
        String[] labels = parts[1].split("\\.", -1);
        boolean invalid = !EMAIL_LOCAL.matcher(local).matches()
                || local.startsWith(".") || local.endsWith(".") || local.contains("..")
                || labels.length < 2
                || labels[labels.length - 1].length() < 2;
        if (!invalid) {
            for (String label : labels) {
                if (!DOMAIN_LABEL.matcher(label).matches()) {
                    invalid = true;
                    break;
                }
            }
        }
        if (invalid) {
            throw ApiException.badRequest("올바른 이메일 형식으로 입력해주세요.");
        }
        return email;
    }

    static String nickname(String value) {
        String nickname = value == null ? "" : value.strip();
        if (nickname.isEmpty()) {
            throw ApiException.badRequest("닉네임을 입력해주세요.");
        }
        if (nickname.length() > 80) {
            throw ApiException.badRequest("닉네임은 80자 이내로 입력해주세요.");
        }
        if (nickname.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw ApiException.badRequest("닉네임에는 줄바꿈이나 제어 문자를 사용할 수 없습니다.");
        }
        return nickname;
    }

    static String password(String value) {
        if (value == null || value.length() < 8 || value.isBlank()) {
            throw ApiException.badRequest("비밀번호는 공백만 사용하지 않고 8자 이상 입력해주세요.");
        }
        if (value.length() > MAX_PASSWORD) {
            throw ApiException.badRequest("비밀번호는 1,024자 이내로 입력해주세요.");
        }
        return value;
    }
}
