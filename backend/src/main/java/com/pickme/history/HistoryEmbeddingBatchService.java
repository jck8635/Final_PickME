package com.pickme.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HistoryEmbeddingBatchService {

    private static final Logger log = LoggerFactory.getLogger(
            HistoryEmbeddingBatchService.class);

    private static final int BATCH_SIZE = 10;

    private final JdbcTemplate jdbcTemplate;
    private final HistoryEmbeddingService embeddingService;

    public HistoryEmbeddingBatchService(
            JdbcTemplate jdbcTemplate,
            HistoryEmbeddingService embeddingService) {

        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    public record BatchResult(
            int total,
            int success,
            int failed,
            List<Integer> failedIds) {
    }

    public BatchResult generateMissing(Integer userId) {

        String sql = """
                SELECT d.id
                FROM public.decision d
                LEFT JOIN public.decision_embedding de
                    ON d.id = de.decision_id
                    AND d.user_id = de.user_id
                WHERE d.user_id = ?
                    AND de.embedding IS NULL
                ORDER BY d.id ASC
                LIMIT ?
                """;

        List<Integer> decisionIds = jdbcTemplate.queryForList(
                sql,
                Integer.class,
                userId,
                BATCH_SIZE);

        int success = 0;

        List<Integer> failedIds = new ArrayList<>();

        for (Integer decisionId : decisionIds) {

            try {
                embeddingService.generate(
                        decisionId, userId);

                success++;

                log.info(
                        "일괄 임베딩 생성 완료: decisionId={}",
                        decisionId);

            } catch (Exception e) {

                failedIds.add(decisionId);

                log.warn(
                        "일괄 임베딩 생성 실패: decisionId={}",
                        decisionId,
                        e);
            }
        }

        return new BatchResult(
                decisionIds.size(),
                success,
                failedIds.size(),
                failedIds);
    }
}
