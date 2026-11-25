package com.pickme.decision;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestHeader;
import com.pickme.chat.ConversationStore;
import com.pickme.common.ApiException;
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
    private final ConversationStore conversations;
    private final DecisionAgentService agent;
    private final UserProfileService userProfileService;

    public DecisionController(
            DecisionService decisionService, UserProfileService userProfileService, ConversationStore conversations,
            DecisionAgentService agent) {
        this.agent = agent;
        this.decisionService = decisionService;
        this.conversations = conversations;
        this.userProfileService = userProfileService;
    }

    @PostMapping("/consult")
    public ConsultResponse consult(@RequestBody ConsultRequest req,
            @RequestHeader(value = "X-Counseling-Session", required = false) UUID sessionId,
            @RequestHeader(value = "X-Decision-Proceed", defaultValue = "false") boolean proceed) {
        Integer userId = LoginUser.currentIdOrNull();

        // 로그인한 사용자 마이페이지 정보 조회

        UUID id = begin(req, userId, sessionId, "decision_consult");
        var outcome = agent.consult(req, userId, proceed);
        ConsultResponse response = new ConsultResponse(outcome.result(), null, outcome.questions());
        conversations.finish(userId, id,
                outcome.result() == null ? String.join("\n", outcome.questions()) : outcome.result(),
                "decision_consult", req);
        return response;
    }

    // 역질문 화면에서 고민을 보내면, ai 가 물어볼 질문 목록 돌려주기
    @PostMapping("/questions")
    public Map<String, List<String>> questions(@RequestBody ConsultRequest req,
            @RequestHeader(value = "X-Counseling-Session", required = false) UUID sessionId) {
        // 서비스에 질문 목록 요청
        Integer userId = LoginUser.currentIdOrNull();
        UUID id = begin(req, userId, sessionId, "decision_questions");
        List<String> questions = decisionService.questions(req);
        conversations.finish(userId, id, String.join("\n", questions), "decision_questions", req);
        // ...형태로 돌려주기
        return Map.of("questions", questions);
    }

    /** 결과 화면에서 스타일만 바꿔 다시 상담 (저장하지 않음) */
    @PostMapping("/restyle")
    public Map<String, String> restyle(@RequestBody ConsultRequest req,
            @RequestHeader(value = "X-Counseling-Session", required = false) UUID sessionId) {
        Integer userId = LoginUser.currentIdOrNull();
        UUID id = begin(req, userId, sessionId, "decision_restyle");
        String result = agent.consult(req, userId, true).result();
        conversations.finish(userId, id, result, "decision_restyle", req);
        return Map.of("result", result);
    }

    private UUID begin(ConsultRequest req, Integer userId, UUID sessionId, String source) {
        if (req.content() == null || req.content().isBlank() || req.content().strip().length() > 5000)
            throw ApiException.badRequest("고민 내용을 5,000자 이내로 입력해 주세요.");
        String style = req.style() != null && List.of("soft", "objective", "fact").contains(req.style())
                ? req.style()
                : "objective";
        return conversations.begin(userId, sessionId, req.title(), style, req.content(), source, req);
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
