package com.pickme.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class HistoryStatsService {

    private final JdbcTemplate jdbc;

    public HistoryStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Summary(
            long totalDecisions,
            long retrospectiveCount,
            Double averageSatisfaction,
            double retrospectiveRate) {
    }

    public record CategoryStats(
            String category,
            long count) {
    }

    public record MonthlyStats(
            String month,
            long count,
            Double averageSatisfaction) {
    }

    public record StatsResponse(
            Summary summary,
            List<CategoryStats> categories,
            List<MonthlyStats> monthly) {
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats(Integer userId) {

        // 전체 고민 수, 회고 작성 수, 평균 만족도, 회고 작성률
        Summary summary = jdbc.queryForObject("""
                SELECT
                    COUNT(*) AS total,

                    COUNT(*) FILTER (
                        WHERE NULLIF(TRIM(actual_choice), '') IS NOT NULL
                           OR satisfaction_score IS NOT NULL
                           OR NULLIF(TRIM(retrospective_note), '') IS NOT NULL
                    ) AS reviewed,

                    ROUND(AVG(satisfaction_score), 2) AS average

                FROM public.decision
                WHERE user_id = ?
                """,
                (rs, n) -> {

                    long total = rs.getLong("total");
                    long reviewed = rs.getLong("reviewed");

                    Double average = nullableDouble(rs, "average");

                    double rate = total == 0
                            ? 0.0
                            : Math.round(
                                    reviewed * 1000.0 / total) / 10.0;

                    return new Summary(
                            total,
                            reviewed,
                            average,
                            rate);
                },
                userId);

        // 카테고리별 고민 분포
        List<CategoryStats> categories = jdbc.query("""
                SELECT
                    COALESCE(
                        NULLIF(TRIM(category), ''),
                        '기타'
                    ) AS category,

                    COUNT(*) AS count

                FROM public.decision
                WHERE user_id = ?

                GROUP BY 1
                ORDER BY count DESC, category ASC
                """,
                (rs, n) -> new CategoryStats(
                        rs.getString("category"),
                        rs.getLong("count")),
                userId);

        // 최근 6개월 고민 수 및 월별 평균 만족도
        List<MonthlyStats> monthly = jdbc.query("""
                WITH months AS (
                    SELECT generate_series(
                        date_trunc(
                            'month',
                            CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul'
                        ) - INTERVAL '5 months',

                        date_trunc(
                            'month',
                            CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul'
                        ),

                        INTERVAL '1 month'
                    ) AS month
                )

                SELECT
                    TO_CHAR(m.month, 'YYYY-MM') AS month,

                    COUNT(d.id) AS count,

                    ROUND(AVG(d.satisfaction_score), 2) AS average

                FROM months m

                LEFT JOIN public.decision d
                    ON d.user_id = ?
                    AND d.created_at >=
                        (m.month AT TIME ZONE 'Asia/Seoul')
                    AND d.created_at <
                        ((m.month + INTERVAL '1 month')
                            AT TIME ZONE 'Asia/Seoul')

                GROUP BY m.month
                ORDER BY m.month ASC
                """,
                (rs, n) -> new MonthlyStats(
                        rs.getString("month"),
                        rs.getLong("count"),
                        nullableDouble(rs, "average")),
                userId);

        return new StatsResponse(
                summary,
                categories,
                monthly);
    }

    // PostgreSQL numeric 타입을 Java Double 타입으로 안전하게 변환
    // 만족도 데이터가 없는 경우 null 유지
    private static Double nullableDouble(
            ResultSet rs,
            String column) throws SQLException {

        BigDecimal value = rs.getBigDecimal(column);

        return value == null
                ? null
                : value.doubleValue();
    }
}
