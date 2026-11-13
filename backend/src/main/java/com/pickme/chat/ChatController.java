package com.pickme.chat;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickme.ai.AiClient;
import com.pickme.common.ApiException;

/** AI 상담. 실제 분석은 FastAPI(/chat) 가 합니다. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_INPUT_LENGTH = 1000;

    private final AiClient aiClient;

    public ChatController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public record ChatRequest(String message, String tone) {
    }

    /** 결정 도우미로 넘길 정리 결과. content 는 원문 질문(결정 도우미 '고민 내용'에 미리 채움). */
    public record DecisionDraft(String title, String category, List<String> options, String content) {
    }

    public record ChatResponse(String reply, DecisionDraft parsedData, boolean isCrisis) {
    }

    @PostMapping
    public ChatResponse send(@RequestBody ChatRequest req) {
        String message = req.message() == null ? "" : req.message();
        if (message.length() > MAX_INPUT_LENGTH) {
            throw ApiException.badRequest("입력은 " + MAX_INPUT_LENGTH + "자 이내로 작성해 주세요.");
        }
        String tone = req.tone() == null ? "empathy" : req.tone();

        AiClient.ChatResult result = aiClient.chat(message, tone);
        AiClient.ParsedDecision parsed = result.isCrisis() ? null : result.parsedData();
        DecisionDraft draft = null;
        if (parsed != null && parsed.options() != null && parsed.options().size() >= 2) {
            draft = new DecisionDraft(
                    parsed.title() == null ? "" : parsed.title(),
                    parsed.category() == null || parsed.category().isBlank() ? "기타" : parsed.category(),
                    parsed.options(),
                    message.strip());
        }
        return new ChatResponse(result.reply(), draft, result.isCrisis());
    }
}
