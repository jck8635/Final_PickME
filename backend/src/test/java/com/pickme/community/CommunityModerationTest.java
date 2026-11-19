package com.pickme.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.pickme.common.ApiException;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CommunityModerationTest {

    @Test
    void ollamaUsesStructuredOutputAndRejectsInvalidVerdicts() throws IOException {
        var mapper = JsonMapper.builder().build();
        var request = new AtomicReference<JsonNode>();
        var response = new AtomicReference<>("{\"decision\":\"ALLOW\"}");
        HttpServer mockOllama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockOllama.createContext("/api/chat", exchange -> {
            try (exchange) {
                request.set(mapper.readTree(exchange.getRequestBody().readAllBytes()));
                byte[] body = mapper.writeValueAsBytes(Map.of("message", Map.of("content", response.get())));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        mockOllama.start();

        try {
            var service = new CommunityModeration(mapper, "ollama", "",
                    "http://127.0.0.1:" + mockOllama.getAddress().getPort(), "", 5);
            var input = Map.of("comment", "오늘 점심은 뭘 먹을까요?");
            assertThat(service.check(input)).isEmpty();
            assertThat(request.get().path("model").asString()).isEqualTo("gpt-oss:20b");
            assertThat(request.get().path("format").path("required").path(0).asString())
                    .isEqualTo("decision");
            assertThat(request.get().path("messages").path(0).path("role").asString()).isEqualTo("system");
            assertThat(request.get().path("messages").path(1).path("content").asString())
                    .isEqualTo(mapper.writeValueAsString(input));

            for (var text : List.of(
                    Map.of("title", "시발"),
                    Map.of("content", "시.발"),
                    Map.of("options", List.of("김밥", "씨발")),
                    Map.of("comment", "ㅅㅂ"))) {
                assertThatThrownBy(() -> service.check(text)).isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
            }
            assertThat(service.check(Map.of("title", "여행의 시발점"))).isEmpty();
            assertThat(service.check(Map.of("title", "가축 질병 신고"))).isEmpty();

            response.set("{\"decision\":\"BLOCK_ADVERTISING\"}");
            assertThatThrownBy(() -> service.check(input)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getMessage()).contains("광고나 스팸"));

            response.set("{\"decision\":\"WARN_HARASSMENT\"}");
            assertThat(service.check(input)).contains("괴롭힘");
            response.set("{\"decision\":\"BLOCK_THREAT\"}");
            assertThatThrownBy(() -> service.check(input)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
            response.set("{\"verdict\":\"ALLOW\",\"reason\":\"THREAT\"}");
            assertThatThrownBy(() -> service.check(input)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        } finally {
            mockOllama.stop(0);
        }
    }
}
