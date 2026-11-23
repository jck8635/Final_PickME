package com.pickme.history;

import com.pickme.member.LoginUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
public class HistorySimilarController {

    private final HistorySimilarService service;

    public HistorySimilarController(
            HistorySimilarService service) {
        this.service = service;
    }

    public record SimilarRequest(String text) {
    }

    @PostMapping("/similar")
    public HistorySimilarService.SimilarResponse search(
            @RequestBody SimilarRequest request) {
        Integer userId = LoginUser.requireId();

        return service.search(userId, request.text());
    }
}
