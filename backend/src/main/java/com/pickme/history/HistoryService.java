package com.pickme.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.common.ApiException;
import com.pickme.common.Categories;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 기존 Flask 의 services/history_service.py. 모든 조회/수정은 본인 기록으로 제한합니다. */
@Service
public class HistoryService {

    private final DecisionRepository decisions;
    private final ObjectMapper objectMapper;

    public HistoryService(DecisionRepository decisions, ObjectMapper objectMapper) {
        this.decisions = decisions;
        this.objectMapper = objectMapper;
    }

    public record OptionView(String title, String note) {
    }

    public record HistoryItem(
            Integer id, String title, String dilemma, String category, Instant createdAt,
            List<OptionView> options, String result, List<String> categories, String style,
            String conditions, String criteria, boolean bookmarked, String actualChoice,
            Integer satisfactionScore, String retrospectiveNote) {
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> list(Integer userId, String query, String category, boolean bookmarkedOnly,
            String sortBy) {
        String cat = category == null || category.isBlank() || Categories.ALL.equals(category) ? null : category;
        String q = query == null ? "" : query.strip();
        String pattern = q.isEmpty() ? null : "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
        Sort sort = "oldest".equals(sortBy)
                ? Sort.by(Sort.Order.asc("createdAt"))
                : Sort.by(Sort.Order.desc("bookmarked"), Sort.Order.desc("createdAt"));
        return decisions.search(userId, cat, bookmarkedOnly, pattern, sort).stream().map(this::toItem).toList();
    }

    @Transactional
    public boolean toggleBookmark(Integer id, Integer userId) {
        Decision d = find(id, userId);
        d.setBookmarked(!d.isBookmarked());
        return d.isBookmarked();
    }

    @Transactional
    public HistoryItem updateRetrospective(Integer id, Integer userId, String actualChoice, Object score,
            String note) {
        Integer satisfaction = parseScore(score);
        Decision d = find(id, userId);
        d.setActualChoice(blankToNull(actualChoice));
        d.setSatisfactionScore(satisfaction);
        d.setRetrospectiveNote(blankToNull(note));
        return toItem(d);
    }

    @Transactional
    public void delete(Integer id, Integer userId) {
        decisions.delete(find(id, userId));
    }

    @Transactional
    public int clear(Integer userId) {
        return decisions.deleteAllByUserId(userId);
    }

    private Decision find(Integer id, Integer userId) {
        return decisions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("기록을 찾을 수 없습니다."));
    }

    static Integer parseScore(Object score) {
        if (score == null || (score instanceof String s && s.isBlank())) {
            return null;
        }
        int value;
        try {
            value = score instanceof Number n ? n.intValue() : Integer.parseInt(score.toString().strip());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("잘못된 만족도 점수 형식입니다.");
        }
        if (value < 1 || value > 5) {
            throw ApiException.badRequest("만족도 점수는 1점에서 5점 사이여야 합니다.");
        }
        return value;
    }

    private HistoryItem toItem(Decision d) {
        List<OptionView> options = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        String result = null;
        String style = null;
        String conditions = null;
        String criteria = null;
        if (d.getResultJson() != null && !d.getResultJson().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(d.getResultJson());
                // H2/일부 드라이버는 JSON 을 문자열로 한 번 더 감싸 저장하므로 풀어줍니다.
                if (root.isString()) {
                    root = objectMapper.readTree(root.asString());
                }
                for (JsonNode opt : root.path("options")) {
                    if (opt.isString()) {
                        options.add(new OptionView(opt.asString(), ""));
                    } else {
                        options.add(new OptionView(opt.path("title").asString(""), opt.path("note").asString("")));
                    }
                }
                for (JsonNode c : root.path("categories")) {
                    categories.add(c.asString());
                }
                result = textOrNull(root, "result");
                style = textOrNull(root, "style");
                conditions = textOrNull(root, "conditions");
                criteria = textOrNull(root, "criteria");
            } catch (JacksonException ignored) {
                // 형식이 깨진 기록은 결과 없이 보여줍니다.
            }
        }
        return new HistoryItem(d.getId(), d.getTitle(), d.getQuestion(), d.getCategory(), d.getCreatedAt(),
                options, result, categories, style, conditions, criteria, d.isBookmarked(), d.getActualChoice(),
                d.getSatisfactionScore(), d.getRetrospectiveNote());
    }

    String toJson(Map<String, Object> value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
