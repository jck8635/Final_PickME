package com.pickme.member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 회원가입 / 로그인 / 로그아웃 / 마이페이지. JWT 기반 로그인입니다. */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final MemberService memberService;
    private final TokenService tokens;
    public AuthController(MemberService memberService, TokenService tokens) {
        this.memberService = memberService; this.tokens = tokens;
    }

    public record SignupRequest(
            String email,
            String nickname,
            String password,
            Integer birthYear,
            String region,
            String occupation,
            String mbti,
            List<String> interests,
            List<String> dislikedFoods,
            String bio) {
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

        UserProfileService.ProfileUpdateRequest profile = new UserProfileService.ProfileUpdateRequest(
                req.birthYear(),
                req.region(),
                req.occupation(),
                req.mbti(),
                req.interests(),
                req.dislikedFoods(),
                req.bio());

        memberService.register(
                req.email(),
                req.nickname(),
                req.password(),
                profile);

        return Map.of(
                "message",
                "회원가입되었습니다. 로그인해주세요.");
    }

    @PostMapping("/auth/login")
    public Map<String,Object> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        tokens.limit("member",req.email(),request.getRemoteAddr());
        User user=memberService.authenticate(req.email(),req.password());
        tokens.clearLimit("member",req.email());
        return Map.of("id",user.getId(),"email",user.getEmail(),"nickname",user.getNickname(),"accessToken",tokens.issue(tokens.member(user),request,response));
    }
    @PostMapping("/auth/refresh")
    public Map<String,String> refresh(HttpServletRequest request,HttpServletResponse response){return Map.of("accessToken",tokens.refresh(request,response));}
    @PostMapping("/auth/logout")
    public Map<String,String> logout(HttpServletRequest request,HttpServletResponse response){tokens.logout(request,response);return Map.of("message","로그아웃되었습니다.");}

    @PatchMapping("/members/me/nickname")
    public MemberView changeNickname(@RequestBody NicknameRequest req) {
        return MemberView.of(memberService.changeNickname(LoginUser.requireId(), req.nickname()));
    }

    @PatchMapping("/members/me/password")
    public Map<String,String> changePassword(@RequestBody PasswordRequest req,HttpServletRequest request,HttpServletResponse response){
        memberService.changePassword(LoginUser.requireId(),req.currentPassword(),req.newPassword(),req.passwordConfirmation());
        tokens.logout(request,response);
        return Map.of("message","비밀번호를 변경했습니다. 다시 로그인해주세요.");
    }
}
