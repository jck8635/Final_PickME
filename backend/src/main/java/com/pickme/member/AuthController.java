package com.pickme.member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** 회원가입 / 로그인 / 로그아웃 / 마이페이지. 세션(JSESSIONID) 기반 로그인입니다. */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final MemberService memberService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    public record SignupRequest(String email, String nickname, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record NicknameRequest(String nickname) {
    }

    public record PasswordRequest(String currentPassword, String newPassword, String passwordConfirmation) {
    }

    public record MemberView(Integer id, String email, String nickname) {
        static MemberView of(User user) {
            return new MemberView(user.getId(), user.getEmail(), user.getNickname());
        }
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me() {
        Integer id = LoginUser.currentIdOrNull();
        Map<String, Object> body = new HashMap<>();
        body.put("user", id == null ? null : MemberView.of(memberService.get(id)));
        return body;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> signup(@RequestBody SignupRequest req) {
        memberService.register(req.email(), req.nickname(), req.password());
        return Map.of("message", "회원가입되었습니다. 로그인해주세요.");
    }

    @PostMapping("/auth/login")
    public MemberView login(@RequestBody LoginRequest req, HttpServletRequest request,
            HttpServletResponse response) {
        User user = memberService.authenticate(req.email(), req.password());
        signIn(user.getId(), request, response);
        return MemberView.of(user);
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("message", "로그아웃되었습니다.");
    }

    @PatchMapping("/members/me/nickname")
    public MemberView changeNickname(@RequestBody NicknameRequest req) {
        return MemberView.of(memberService.changeNickname(LoginUser.requireId(), req.nickname()));
    }

    @PatchMapping("/members/me/password")
    public Map<String, String> changePassword(@RequestBody PasswordRequest req, HttpServletRequest request) {
        memberService.changePassword(LoginUser.requireId(), req.currentPassword(), req.newPassword(),
                req.passwordConfirmation());
        // 비밀번호 변경 후 세션 ID 를 새로 발급 (Flask 의 session.clear() 후 재로그인에 해당)
        request.changeSessionId();
        return Map.of("message", "비밀번호를 변경했습니다.");
    }

    private void signIn(Integer userId, HttpServletRequest request, HttpServletResponse response) {
        // 세션 고정 공격 방지: 기존 세션을 버리고 새로 만듭니다.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
