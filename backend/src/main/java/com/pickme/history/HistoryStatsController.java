package com.pickme.history;
import com.pickme.member.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryStatsController {
    private final HistoryStatsService statsService;
    public HistoryStatsController(HistoryStatsService statsService) { this.statsService = statsService; }
    @GetMapping("/stats")
    public HistoryStatsService.StatsResponse getStats() { return statsService.getStats(LoginUser.requireId()); }
}
