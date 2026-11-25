package com.pickme.community;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.pickme.member.AdminAccess;

@RestController
@RequestMapping("/api/community")
public class ReportController {

    private final ReportService reports;
    private final AdminAccess admins;

    public ReportController(
            ReportService reports,
            AdminAccess admins) {

        this.reports = reports;
        this.admins = admins;
    }

    public record ReportRequest(String reason) {
    }

    @GetMapping("/reporting")
    public Map<String, Boolean> available() {
        return Map.of("available", admins.available());
    }

    @PostMapping("/posts/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> report(
            @PathVariable Integer id,
            @RequestBody ReportRequest req) {

        reports.report(id, req.reason());

        return Map.of("message", "신고가 접수되었습니다.");
    }
}
