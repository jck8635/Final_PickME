package com.pickme.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.ai.AiClient;
import com.pickme.common.ApiException;
import com.pickme.common.Categories;
import com.pickme.history.Decision;
import com.pickme.history.DecisionRepository;
import com.pickme.member.UserRepository;

import tools.jackson.databind.ObjectMapper;

/** 기존 Flask 의 routes/decision_routes.py 의 저장/프롬프트 준비 부분. */
@Service
public class DecisionService {

    private static final Set<String> STYLES = Set.of("soft", "objective", "fact");

    private final AiClient aiClient;
    private final DecisionRepository decisions;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public DecisionService(AiClient aiClient, DecisionRepository decisions, UserRepository users,
            ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.decisions = decisions;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    public record Option(String title, String note) {
    }

    /** weight: "1"~"5" 또는 "보류" */
    public record Criterion(String name, String weight) {
    }

    public record ConsultRequest(
            List<String> categories, String style, String title, String content,
            List<String> conditions, String conditionsFree, List<Option> options, List<Criterion> criteria) {
    }

    public record ConsultResponse(String result, Integer decisionId) {
    }

    public record PopularItem(String title, long count) {
    }

    record Prepared(List<String> categories, String style, String title, String content, String conditions,
            List<AiClient.DecisionOption> options, String criteria) {
    }

    public ConsultResponse consult(ConsultRequest req, Integer userId) {
        Prepared p = prepare(req);
        String result = aiClient.consult(toAiRequest(p));
        Integer savedId = userId == null ? null : save(userId, p, result);
        return new ConsultResponse(result, savedId);
    }

    public String restyle(ConsultRequest req) {
        return aiClient.consult(toAiRequest(prepare(req)));
    }

    @Transactional(readOnly = true)
    public List<PopularItem> popular() {
        return decisions.popularTitles(PageRequest.of(0, 5)).stream()
                .map(row -> new PopularItem((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /** AI 호출(수 초)이 트랜잭션 밖에서 끝난 뒤 저장만 짧게 합니다. */
    private Integer save(Integer userId, Prepared p, String result) {
        var user = users.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("categories", p.categories());
        json.put("style", p.style());
        json.put("conditions", p.conditions());
        json.put("options", p.options());
        json.put("criteria", p.criteria());
        json.put("result", result);

        String title = p.title().isEmpty() ? truncate(p.content(), 50) : truncate(p.title(), 200);
        // 원본은 category 를 저장하지 않아 기록 화면의 카테고리 필터가 동작하지 않았음 → 첫 번째 카테고리 저장
        String category = p.categories().isEmpty() ? "기타" : p.categories().get(0);
        Decision saved = decisions.save(new Decision(user, title, p.content(), category,
                objectMapper.writeValueAsString(json)));
        return saved.getId();
    }

    private Prepared prepare(ConsultRequest req) {
        String content = strip(req.content());
        if (content.isEmpty()) {
            throw ApiException.badRequest("고민 내용을 입력해 주세요.");
        }
        if (content.length() > 5000) {
            throw ApiException.badRequest("고민 내용은 5,000자 이내로 입력해 주세요.");
        }
        List<String> categories = nonNull(req.categories()).stream()
                .filter(Categories.DECISION::contains).distinct().toList();
        String style = req.style() != null && STYLES.contains(req.style()) ? req.style() : "objective";
        String title = truncate(strip(req.title()), 200);

        // 칩(체크박스) + 자유 입력 조건을 모두 반영
        List<String> conditionParts = new ArrayList<>(nonNull(req.conditions()).stream()
                .map(DecisionService::strip).filter(s -> !s.isEmpty()).toList());
        String free = strip(req.conditionsFree());
        if (!free.isEmpty()) {
            conditionParts.add(free);
        }
        String conditions = truncate(String.join(", ", conditionParts), 1000);

        List<AiClient.DecisionOption> options = nonNull(req.options()).stream()
                .filter(o -> o != null && !strip(o.title()).isEmpty())
                .limit(5)
                .map(o -> new AiClient.DecisionOption(truncate(strip(o.title()), 200), truncate(strip(o.note()), 500)))
                .toList();

        // "기준명(가중치점)" 형태로 정리해 AI 에게 전달
        String criteria = nonNull(req.criteria()).stream()
                .filter(c -> c != null && !strip(c.name()).isEmpty())
                .map(c -> {
                    String w = strip(c.weight());
                    String label = w.isEmpty() ? "?점" : ("보류".equals(w) ? "보류" : w + "점");
                    return strip(c.name()) + "(" + label + ")";
                })
                .distinct()
                .collect(Collectors.joining(", "));
        if (criteria.isEmpty()) {
            criteria = "지정 없음";
        }
        return new Prepared(categories, style, title, content, conditions, options, truncate(criteria, 1000));
    }

    private static AiClient.DecisionRequest toAiRequest(Prepared p) {
        return new AiClient.DecisionRequest(p.categories(), p.style(), p.title(), p.content(), p.conditions(),
                p.options(), p.criteria());
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
