package com.pickme.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.common.ApiException;
import com.pickme.common.Categories;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class HistoryService {

    private final DecisionRepository decisions;
    private final ObjectMapper objectMapper;

    public HistoryService(
            DecisionRepository decisions,
            ObjectMapper objectMapper) {
        this.decisions = decisions;
        this.objectMapper = objectMapper;
    }

    public record OptionView(String title, String note) {
    }

    public record HistoryItem(
            Integer id,
            String title,
            String dilemma,
            String category,
            Instant createdAt,
            List<OptionView> options,
            String result,
            List<String> categories,
            String style,
            String conditions,
            String criteria,
            boolean bookmarked,
            String actualChoice,
            Integer satisfactionScore,
            String retrospectiveNote) {
    }

    public record HistoryPage(
            List<HistoryItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    /**
     * 기존 전체 목록 API용 메서드.
     * 기존 호출 코드와의 호환성을 위해 유지합니다.
     */
    @Transactional(readOnly = true)
    public List<HistoryItem> list(
            Integer userId,
            String query,
            String category,
            boolean bookmarkedOnly,
            String sortBy) {

        String cat = category == null
                || category.isBlank()
                || Categories.ALL.equals(category)
                ? null
                : category;

        String q = query == null ? "" : query.strip();

        String pattern = q.isEmpty()
                ? null
                : "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";

        return decisions.search(
                        userId,
                        cat,
                        bookmarkedOnly,
                        pattern,
                        historySort(sortBy))
                .stream()
                .map(this::toItem)
                .toList();
    }

    /**
     * 페이지 단위 목록 조회.
     * page는 0부터 시작하며 size는 1~100만 허용합니다.
     */
    @Transactional(readOnly = true)
    public HistoryPage page(
            Integer userId,
            String query,
            String category,
            boolean bookmarkedOnly,
            String sortBy,
            int page,
            int size) {

        if (page < 0
                || size < 1
                || size > 100
                || (long) page * size > Integer.MAX_VALUE) {
            throw ApiException.badRequest(
                    "페이지는 0 이상, 페이지 크기는 1~100으로 입력해주세요.");
        }

        String cat = category == null
                || category.isBlank()
                || Categories.ALL.equals(category)
                ? null
                : category;

        String q = query == null ? "" : query.strip();

        String pattern = q.isEmpty()
                ? null
                : "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";

        Page<Decision> found = decisions.searchPage(
                userId,
                cat,
                bookmarkedOnly,
                pattern,
                PageRequest.of(page, size, historySort(sortBy)));

        return new HistoryPage(
                found.getContent().stream()
                        .map(this::toItem)
                        .toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }

    /**
     * 생성 시각이 같아도 페이지 사이의 순서가 일정하도록
     * 마지막 정렬 조건에 id를 추가합니다.
     */
    private static Sort historySort(String sortBy) {
        return "oldest".equals(sortBy)
                ? Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id"))
                : Sort.by(
                        Sort.Order.desc("bookmarked"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"));
    }

    @Transactional
    public boolean toggleBookmark(Integer id, Integer userId) {
        Decision d = find(id, userId);
        d.setBookmarked(!d.isBookmarked());
        return d.isBookmarked();
    }

    @Transactional
    public HistoryItem updateRetrospective(
            Integer id,
            Integer userId,
            String actualChoice,
            Object score,
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
                .orElseThrow(
                        () -> ApiException.notFound("기록을 찾을 수 없습니다."));
    }

    static Integer parseScore(Object score) {
        if (score == null
                || (score instanceof String s && s.isBlank())) {
            return null;
        }

        int value;

        try {
            value = score instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(score.toString().strip());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("잘못된 만족도 점수 형식입니다.");
        }

        if (value < 1 || value > 5) {
            throw ApiException.badRequest(
                    "만족도 점수는 1점에서 5점 사이여야 합니다.");
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

        if (d.getResultJson() != null
                && !d.getResultJson().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(d.getResultJson());

                // 일부 DB 드라이버가 JSON을 문자열로 감싼 경우 처리합니다.
                if (root.isString()) {
                    root = objectMapper.readTree(root.asString());
                }

                for (JsonNode opt : root.path("options")) {
                    if (opt.isString()) {
                        options.add(new OptionView(opt.asString(), ""));
                    } else {
                        options.add(new OptionView(
                                opt.path("title").asString(""),
                                opt.path("note").asString("")));
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
                // 기존 JSON 형식이 깨졌더라도 기록 자체는 표시합니다.
            }
        }

        return new HistoryItem(
                d.getId(),
                d.getTitle(),
                d.getQuestion(),
                d.getCategory(),
                d.getCreatedAt(),
                options,
                result,
                categories,
                style,
                conditions,
                criteria,
                d.isBookmarked(),
                d.getActualChoice(),
                d.getSatisfactionScore(),
                d.getRetrospectiveNote());
    }

    String toJson(Map<String, Object> value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull()
                ? null
                : value.asString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.strip();
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
