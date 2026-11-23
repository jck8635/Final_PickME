
package com.pickme.history;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickme.member.LoginUser;

@RestController
@RequestMapping("/api/history")
public class HistoryEmbeddingController {

    private final HistoryEmbeddingService embeddingService;

    public HistoryEmbeddingController(
            HistoryEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/{id}/embedding")
    public HistoryEmbeddingService.EmbeddingResult generate(
            @PathVariable("id") Integer id) {

        return embeddingService.generate(
                id,
                LoginUser.requireId());
    }
}
