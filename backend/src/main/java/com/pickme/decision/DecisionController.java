package com.pickme.decision;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickme.decision.DecisionService.ConsultRequest;
import com.pickme.decision.DecisionService.ConsultResponse;
import com.pickme.member.LoginUser;

/** 결정 도우미. 로그인 없이도 상담할 수 있고, 로그인 상태면 고민 기록에 저장됩니다. */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping("/consult")
    public ConsultResponse consult(@RequestBody ConsultRequest req) {
        return decisionService.consult(req, LoginUser.currentIdOrNull());
    }

    /** 결과 화면에서 스타일만 바꿔 다시 상담 (저장하지 않음) */
    @PostMapping("/restyle")
    public Map<String, String> restyle(@RequestBody ConsultRequest req) {
        return Map.of("result", decisionService.restyle(req));
    }

    @GetMapping("/popular")
    public List<DecisionService.PopularItem> popular() {
        return decisionService.popular();
    }
}
