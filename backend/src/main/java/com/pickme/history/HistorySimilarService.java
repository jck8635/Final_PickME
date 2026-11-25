package com.pickme.history;

import com.pickme.ai.AiClient;
import com.pickme.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HistorySimilarService {
    private static final Logger log = LoggerFactory.getLogger(HistorySimilarService.class);
    private final AiClient aiClient;
    private final JdbcTemplate jdbcTemplate;
    public HistorySimilarService(AiClient aiClient, JdbcTemplate jdbcTemplate) {
        this.aiClient = aiClient;
        this.jdbcTemplate = jdbcTemplate;
    }
    public record Candidate(Integer decisionId, String title, String question,
            String actualChoice, String retrospectiveNote, Integer satisfactionScore,
            Double similarity, int keywordHits) {}
    public record SimilarDecision(Integer decisionId, String title, String actualChoice,
            String retrospectiveNote, Double similarity, Integer relevance) {}
    public record SimilarResponse(List<SimilarDecision> results, String summary,
            String mode, String notice) {}

    private static final String FIELDS = "d.id, d.title, d.question, d.actual_choice, d.retrospective_note, d.satisfaction_score";
    private Candidate read(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        double sim = rs.getDouble("similarity");
        Double similarity = rs.wasNull() ? null : sim;
        int score = rs.getInt("satisfaction_score");
        Integer satisfaction = rs.wasNull() ? null : score;
        return new Candidate(rs.getInt("id"), rs.getString("title"), rs.getString("question"),
                rs.getString("actual_choice"), rs.getString("retrospective_note"), satisfaction,
                similarity, rs.getInt("keyword_hits"));
    }
    public SimilarResponse search(Integer userId, String text) {
        if (text == null || text.isBlank() || text.length() > 5000)
            throw ApiException.badRequest("검색어는 1~5000자로 입력해주세요.");
        text = text.strip();
        List<String> tokens = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 2).distinct().limit(12).toList();
        if (tokens.isEmpty()) tokens = List.of(text.toLowerCase(Locale.ROOT));
        String haystack = "lower(concat_ws(' ', d.title, d.question, d.actual_choice, d.retrospective_note))";
        String hits = tokens.stream().map(t -> "CASE WHEN strpos(" + haystack + ", ?) > 0 THEN 1 ELSE 0 END")
                .collect(Collectors.joining(" + "));
        List<Object> args = new ArrayList<>(tokens);
        args.add(userId);
        List<Candidate> keyword = jdbcTemplate.query("SELECT * FROM (SELECT " + FIELDS
                + ", NULL::double precision AS similarity, (" + hits + ") AS keyword_hits"
                + " FROM public.decision d WHERE d.user_id = ?) k WHERE keyword_hits > 0"
                + " ORDER BY keyword_hits DESC, id DESC LIMIT 10", this::read, args.toArray());
        List<Candidate> vector = List.of();
        boolean embeddingFailed = false;
        try {
            var embedding = aiClient.createQueryEmbedding(text).embedding();
            if (embedding.stream().anyMatch(v -> v == null || !Double.isFinite(v)))
                throw new IllegalStateException("Invalid embedding");
            String v = embedding.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
            vector = jdbcTemplate.query("SELECT " + FIELDS + """
                , 1 - (de.embedding <=> ?::extensions.vector) AS similarity, 0 AS keyword_hits
                FROM public.decision_embedding de JOIN public.decision d
                  ON d.id = de.decision_id AND d.user_id = de.user_id
                WHERE d.user_id = ? AND de.embedding IS NOT NULL
                  AND (1 - (de.embedding <=> ?::extensions.vector)) >= 0.50
                ORDER BY de.embedding <=> ?::extensions.vector, d.id LIMIT 10
                """, this::read, v, userId, v, v);
        } catch (RuntimeException e) {
            embeddingFailed = true;
            log.warn("History vector search unavailable: {}", e.getClass().getSimpleName());
        }
        Map<Integer, Candidate> merged = new HashMap<>();
        Map<Integer, Double> ranks = new HashMap<>();
        for (int i=0; i<keyword.size(); i++) {
            Candidate c=keyword.get(i); merged.put(c.decisionId(), c);
            ranks.merge(c.decisionId(), 1.0/(60+i+1), Double::sum);
        }
        for (int i=0; i<vector.size(); i++) {
            Candidate c=vector.get(i); Candidate k=merged.get(c.decisionId());
            merged.put(c.decisionId(), new Candidate(c.decisionId(), c.title(), c.question(), c.actualChoice(),
                    c.retrospectiveNote(), c.satisfactionScore(), c.similarity(), k==null?0:k.keywordHits()));
            ranks.merge(c.decisionId(), 1.0/(60+i+1), Double::sum);
        }
        List<Candidate> candidates=merged.values().stream().sorted(Comparator
                .<Candidate>comparingDouble(c -> ranks.get(c.decisionId())).reversed()
                .thenComparing(Candidate::decisionId)).limit(10).toList();
        String notice=embeddingFailed?"벡터 검색을 사용할 수 없어 키워드 후보를 사용했습니다.":null;
        if(candidates.isEmpty()) return new SimilarResponse(List.of(),null,"hybrid",notice);
        try {
            var evaluated=aiClient.rankHistory(text,candidates);
            Map<Integer,Integer> scores=new HashMap<>();
            for(var r:evaluated.ranking()) {
                if(!merged.containsKey(r.decisionId()) || candidates.stream().noneMatch(c->c.decisionId().equals(r.decisionId()))
                        || r.score()<0 || r.score()>100 || scores.putIfAbsent(r.decisionId(),r.score())!=null)
                    throw new IllegalStateException("Invalid ranking");
            }
            if(scores.size()!=candidates.size()) throw new IllegalStateException("Incomplete ranking");
            List<Candidate> selected=candidates.stream().filter(c->scores.get(c.decisionId())>=70)
                    .sorted(Comparator.<Candidate>comparingInt(c->scores.get(c.decisionId())).reversed()
                            .thenComparing(c->ranks.get(c.decisionId()),Comparator.reverseOrder())
                            .thenComparing(Candidate::decisionId)).limit(3).toList();
            String summary=null;
            if(!selected.isEmpty()) {
                try { summary=aiClient.summarizeHistory(text,selected).summary(); }
                catch(RuntimeException e) { notice=(notice==null?"":notice+" ")+"AI 요약을 생성하지 못했습니다."; }
            }
            return new SimilarResponse(selected.stream().map(c->result(c,scores.get(c.decisionId()))).toList(),summary,"reranked",notice);
        } catch(RuntimeException e) {
            log.warn("History reranking unavailable: {}",e.getClass().getSimpleName());
            return new SimilarResponse(candidates.stream().filter(c->c.keywordHits()>0 || (c.similarity()!=null && c.similarity()>=0.65))
                    .limit(3).map(c->result(c,null)).toList(),null,"hybrid",
                    (notice==null?"":notice+" ")+"AI 관련성 평가를 사용할 수 없어 기본 검색 결과를 표시합니다.");
        }
    }
    private static SimilarDecision result(Candidate c,Integer relevance) {
        return new SimilarDecision(c.decisionId(),c.title(),c.actualChoice(),c.retrospectiveNote(),c.similarity(),relevance);
    }
}
