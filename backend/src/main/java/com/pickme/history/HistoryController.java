package com.pickme.history;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickme.member.LoginUser;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    public record RetrospectiveRequest(
            String actualChoice,
            Object satisfactionScore,
            String retrospectiveNote) {
    }

    /**
     * 기존 배열 응답을 유지하는 API.
     */
    @GetMapping
    public List<HistoryService.HistoryItem> list(
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "category", defaultValue = "전체")
            String category,
            @RequestParam(name = "bookmarked", defaultValue = "false")
            boolean bookmarked,
            @RequestParam(name = "sort", defaultValue = "latest")
            String sort) {

        return historyService.list(
                LoginUser.requireId(),
                q.length() > 100 ? q.substring(0, 100) : q,
                category,
                bookmarked,
                sort);
    }

    /**
     * 페이지 조회 API.
     * 예: GET /api/history/page?page=0&size=10
     */
    @GetMapping("/page")
    public HistoryService.HistoryPage page(
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "category", defaultValue = "전체")
            String category,
            @RequestParam(name = "bookmarked", defaultValue = "false")
            boolean bookmarked,
            @RequestParam(name = "sort", defaultValue = "latest")
            String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return historyService.page(
                LoginUser.requireId(),
                q.length() > 100 ? q.substring(0, 100) : q,
                category,
                bookmarked,
                sort,
                page,
                size);
    }

    @PostMapping("/{id}/bookmark")
    public Map<String, Boolean> toggleBookmark(
            @PathVariable("id") Integer id) {

        return Map.of(
                "bookmarked",
                historyService.toggleBookmark(id, LoginUser.requireId()));
    }

    @PutMapping("/{id}/retrospective")
    public HistoryService.HistoryItem updateRetrospective(
            @PathVariable("id") Integer id,
            @RequestBody RetrospectiveRequest req) {

        return historyService.updateRetrospective(
                id,
                LoginUser.requireId(),
                req.actualChoice(),
                req.satisfactionScore(),
                req.retrospectiveNote());
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(
            @PathVariable("id") Integer id) {

        historyService.delete(id, LoginUser.requireId());
        return Map.of("success", true);
    }

    @DeleteMapping
    public Map<String, Integer> clear() {
        return Map.of(
                "deleted",
                historyService.clear(LoginUser.requireId()));
    }
}
