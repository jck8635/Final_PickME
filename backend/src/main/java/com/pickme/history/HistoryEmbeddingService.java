
package com.pickme.history;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.pickme.ai.AiClient;
import com.pickme.common.ApiException;

@Service
public class HistoryEmbeddingService {

    private final DecisionRepository decisions;
    private final AiClient aiClient;
    private final JdbcTemplate jdbcTemplate;

    public HistoryEmbeddingService(
            DecisionRepository decisions,
            AiClient aiClient,
            JdbcTemplate jdbcTemplate) {
        this.decisions = decisions;
        this.aiClient = aiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record EmbeddingResult(
            Integer decisionId,
            Integer userId,
            String model,
            int dimension,
            String status) {
    }

    public EmbeddingResult generate(
            Integer decisionId,
            Integer userId) {

        // 반드시 로그인한 사용자의 기록만 조회
        Decision decision = decisions
                .findByIdAndUserId(decisionId, userId)
                .orElseThrow(() -> ApiException.notFound(
                        "결정 기록을 찾을 수 없습니다."));

        // 제목과 고민 내용을 결합
        String text = buildText(decision);

        // 원본 내용 변경 여부 확인용 해시
        String sourceHash = sha256(text);

        // FastAPI -> Gemini 임베딩 생성
        AiClient.EmbeddingResponse response = aiClient.createEmbedding(text);

        // PostgreSQL pgvector 형식으로 변환
        String vector = toVector(response.embedding());

        // 동일 decision_id가 있으면 갱신
        String sql = """
                INSERT INTO public.decision_embedding (
                    decision_id,
                    user_id,
                    embedding,
                    model_name,
                    source_hash
                )
                VALUES (?, ?, ?::extensions.vector, ?, ?)
                ON CONFLICT (decision_id)
                DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    embedding = EXCLUDED.embedding,
                    model_name = EXCLUDED.model_name,
                    source_hash = EXCLUDED.source_hash,
                    updated_at = NOW()
                """;

        jdbcTemplate.update(
                sql,
                decisionId,
                userId,
                vector,
                response.model(),
                sourceHash);

        return new EmbeddingResult(
                decisionId,
                userId,
                response.model(),
                response.dimension(),
                "saved");
    }

    private static String buildText(Decision decision) {
        String title = decision.getTitle() == null
                ? ""
                : decision.getTitle().strip();

        String question = decision.getQuestion() == null
                ? ""
                : decision.getQuestion().strip();

        String text = "제목: " + title
                + "\n고민: " + question;

        if (text.length() > 10000) {
            text = text.substring(0, 10000);
        }

        return text;
    }

    private static String toVector(List<Double> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);

            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "유효하지 않은 임베딩 값입니다.");
            }

            if (i > 0) {
                builder.append(",");
            }
            builder.append(value);
        }
        return builder.append("]").toString();
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
