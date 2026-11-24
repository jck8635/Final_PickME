package com.pickme.history;

import com.pickme.ai.AiClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistorySimilarService {

    private static final double MIN_SIMILARITY = 0.65;

    private final AiClient aiClient;
    private final JdbcTemplate jdbcTemplate;

    public HistorySimilarService(
            AiClient aiClient,
            JdbcTemplate jdbcTemplate) {
        this.aiClient = aiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SimilarDecision(
            Integer decisionId,
            String title,
            String actualChoice,
            String retrospectiveNote,
            double similarity) {
    }

    public record SimilarResponse(
            List<SimilarDecision> results) {
    }

    public SimilarResponse search(
            Integer userId,
            String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "검색할 고민을 입력해주세요.");
        }

        if (text.length() > 5000) {
            throw new IllegalArgumentException(
                    "검색할 고민은 5000자 이하여야 합니다.");
        }

        var response = aiClient.createQueryEmbedding(text);

        String vector = response.embedding()
                .stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(
                        ",", "[", "]"));

        String sql = """
                SELECT
                    d.id,
                    d.title,
                    d.actual_choice,
                    d.retrospective_note,
                    1 - (
                        de.embedding <=> ?::extensions.vector
                    ) AS similarity
                FROM public.decision_embedding de
                JOIN public.decision d
                    ON d.id = de.decision_id
                   AND d.user_id = de.user_id
                WHERE de.user_id = ?
                  AND de.embedding IS NOT NULL
                  AND (1 - (de.embedding <=> ?::extensions.vector)) >= ?
                ORDER BY de.embedding <=> ?::extensions.vector
                LIMIT 3
                """;

        List<SimilarDecision> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SimilarDecision(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("actual_choice"),
                        rs.getString("retrospective_note"),
                        rs.getDouble("similarity")),
                vector,
                userId,
                vector,
                MIN_SIMILARITY,
                vector);

        return new SimilarResponse(results);
    }
}
