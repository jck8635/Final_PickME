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

/** 고민 기록. 로그인 필요 (원본은 비로그인 시 user_id=1 로 처리하던 임시 코드가 있었음). */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    public record RetrospectiveRequest(String actualChoice, Object satisfactionScore, String retrospectiveNote) {
    }

    @GetMapping
    public List<HistoryService.HistoryItem> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "전체") String category,
            @RequestParam(defaultValue = "false") boolean bookmarked,
            @RequestParam(defaultValue = "latest") String sort) {
        return historyService.list(LoginUser.requireId(), q.length() > 100 ? q.substring(0, 100) : q,
                category, bookmarked, sort);
    }

    @PostMapping("/{id}/bookmark")
    public Map<String, Boolean> toggleBookmark(@PathVariable Integer id) {
        return Map.of("bookmarked", historyService.toggleBookmark(id, LoginUser.requireId()));
    }

    @PutMapping("/{id}/retrospective")
    public HistoryService.HistoryItem updateRetrospective(@PathVariable Integer id,
            @RequestBody RetrospectiveRequest req) {
        return historyService.updateRetrospective(id, LoginUser.requireId(), req.actualChoice(),
                req.satisfactionScore(), req.retrospectiveNote());
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable Integer id) {
        historyService.delete(id, LoginUser.requireId());
        return Map.of("success", true);
    }

    @DeleteMapping
    public Map<String, Integer> clear() {
        return Map.of("deleted", historyService.clear(LoginUser.requireId()));
    }
}
