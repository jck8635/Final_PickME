
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
    private final HistoryEmbeddingBatchService batchService;

    public HistoryEmbeddingController(
            HistoryEmbeddingService embeddingService,
            HistoryEmbeddingBatchService batchService) {

        this.embeddingService = embeddingService;
        this.batchService = batchService;
    }

    @PostMapping("/{id}/embedding")
    public HistoryEmbeddingService.EmbeddingResult generate(
            @PathVariable("id") Integer id) {

        return embeddingService.generate(
                id,
                LoginUser.requireId());
    }

    @PostMapping("/embedding/batch")
    public HistoryEmbeddingBatchService.BatchResult generateBatch() {

        Integer userId = LoginUser.requireId();

        return batchService.generateMissing(userId);
    }
}
