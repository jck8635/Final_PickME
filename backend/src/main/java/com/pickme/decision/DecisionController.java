package com.pickme.decision;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickme.decision.DecisionService.ConsultRequest;
import com.pickme.decision.DecisionService.ConsultResponse;
import com.pickme.member.LoginUser;
import com.pickme.member.UserProfileService; 

/** 결정 도우미. 로그인 없이도 상담할 수 있고, 로그인 상태면 고민 기록에 저장됩니다. */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionService decisionService;
    private final UserProfileService userProfileService;
    


    public DecisionController(
        DecisionService decisionService, UserProfileService userProfileService) {
        this.decisionService = decisionService;
        this.userProfileService = userProfileService;
    }

    @PostMapping("/consult")
    public ConsultResponse consult(@RequestBody ConsultRequest req) {
        Integer userId = LoginUser.currentIdOrNull();

        // 로그인한 사용자 마이페이지 정보 조회

        var userProfile = userId != null
        ? userProfileService.getProfile(userId) 
        : null;

        return decisionService.consult(req, userId, userProfile);
    }

    // 역질문 화면에서 고민을 보내면,  ai 가 물어볼 질문 목록  돌려주기
    @PostMapping("/questions")
    public Map<String, List<String>> questions (@RequestBody ConsultRequest req) {
        // 서비스에 질문 목록 요청
        List<String> questions = decisionService.questions(req);
        // ...형태로 돌려주기
        return Map.of("questions", questions);
    }
    



    /** 결과 화면에서 스타일만 바꿔 다시 상담 (저장하지 않음) */
    @PostMapping("/restyle")
    public Map<String, String> restyle(@RequestBody ConsultRequest req) {
        return Map.of("result", decisionService.restyle(req, LoginUser.currentIdOrNull() == null ? null : userProfileService.getProfile(LoginUser.currentIdOrNull())));
    }

    @GetMapping("/popular")
    public List<DecisionService.PopularItem> popular() {
        return decisionService.popular();
    }


   // 수동 저장: [저장하기] 버튼을 누를 때만 DB에 저장함
    @PostMapping("/save")
    public ResponseEntity<?> saveDecision(@RequestBody ConsultRequest req) {
        Integer userId = LoginUser.currentIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        
        Integer savedId = decisionService.saveManual(userId, req, req.result());

        if (savedId == null) {
            return ResponseEntity.badRequest().body("저장에 실패했습니다.");
        }

        return ResponseEntity.ok(Map.of("decisionId", savedId, "message", "성공적으로 저장되었습니다."));
    }

}
