package com.pickme.ai;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** React -> Spring -> FastAPI 연결 확인용. AI상담/결정도우미 API 는 이 패키지에 추가합니다. */
@RestController
@RequestMapping("/api/ai")
public class AiHealthController {

    private final RestClient aiRestClient;

    public AiHealthController(RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        try {
            aiRestClient.get().uri("/health").retrieve().toBodilessEntity();
            return Map.of("service", "pickme-ai", "status", "ok");
        } catch (RestClientException e) {
            return Map.of("service", "pickme-ai", "status", "down");
        }
    }
}
