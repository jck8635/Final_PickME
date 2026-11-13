package com.pickme.ai;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pickme.common.ApiException;

/**
 * FastAPI(AI 서버) 호출. FastAPI 의 오류 {"detail": "..."} 는 같은 상태코드의 ApiException 으로 바꿉니다.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient aiRestClient;

    public AiClient(RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParsedDecision(String title, String category, List<String> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResult(
            String reply,
            @JsonProperty("parsed_data") ParsedDecision parsedData,
            @JsonProperty("is_crisis") boolean isCrisis) {
    }

    public record DecisionOption(String title, String note) {
    }

    public record DecisionRequest(
            List<String> categories, String style, String title, String content,
            String conditions, List<DecisionOption> options, String criteria) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecisionResult(String result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(Object detail) {
    }

    public ChatResult chat(String message, String tone) {
        return post("/chat", Map.of("message", message, "tone", tone), ChatResult.class);
    }

    public String consult(DecisionRequest request) {
        return post("/decision/consult", request, DecisionResult.class).result();
    }

    private <T> T post(String uri, Object body, Class<T> type) {
        try {
            T result = aiRestClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (result == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI 서버 응답이 비어 있습니다.");
            }
            return result;
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            String message = detailOf(e);
            if (status != null && status.is4xxClientError() && message != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, message);
            }
            log.warn("AI 서버 오류 {} {}: {}", uri, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    message != null ? message : "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("AI 서버 연결 실패 {}: {}", uri, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 서버에 연결할 수 없습니다. FastAPI(8000) 가 실행 중인지 확인해 주세요.");
        }
    }

    private static String detailOf(RestClientResponseException e) {
        try {
            ErrorBody body = e.getResponseBodyAs(ErrorBody.class);
            if (body != null && body.detail() instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // 형식이 다르면 기본 메시지 사용 (예: 422 검증 오류는 detail 이 배열)
        }
        return null;
    }
}
