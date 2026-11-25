package com.pickme.decision;

import java.util.*;
import org.springframework.stereotype.Service;
import com.pickme.ai.AiClient;
import com.pickme.history.HistorySimilarService;
import com.pickme.member.UserProfileService;
import org.springframework.beans.factory.annotation.Value;

@Service
public class DecisionAgentService {
    private final AiClient ai;
    private final DecisionService decisions;
    private final HistorySimilarService history;
    private final UserProfileService profiles;
    private final boolean enabled;
    public DecisionAgentService(AiClient ai, DecisionService decisions, HistorySimilarService history,
            UserProfileService profiles,
            @Value("${pickme.agent.enabled:false}") boolean enabled) {
        this.ai=ai; this.decisions=decisions; this.history=history; this.profiles=profiles;
        this.enabled=enabled;
    }
    public record Outcome(String result, List<String> questions) {}
    public Outcome consult(DecisionService.ConsultRequest req, Integer userId, boolean proceed) {
        if (!enabled) return new Outcome(decisions.consult(req,userId,userId==null?null:profiles.getProfile(userId)).result(), List.of());
        var prepared=decisions.prepare(req);
        var request=DecisionService.toAiRequest(prepared,null);
        var plan=ai.plan(request);
        if (!proceed && plan.missing()!=null && !plan.missing().isEmpty())
            return new Outcome(null,plan.missing().stream().limit(3).toList());
        Map<String,Object> evidence=new LinkedHashMap<>();
        var tools=plan.tools()==null?List.<String>of():plan.tools();
        for(String tool:new LinkedHashSet<>(tools)) {
            if(userId==null) break;
            try {
                switch(tool) {
                    case "profile" -> { var profile=profiles.getProfile(userId); if(profile!=null) evidence.put(tool,DecisionService.toAiRequest(prepared,profile).userProfile()); }
                    case "history" -> evidence.put(tool,history.search(userId,request.content()));
                    default -> { }
                }
            } catch(RuntimeException ex) { evidence.put(tool,Map.of("status","unavailable")); }
        }
        return new Outcome(ai.evaluate(request,plan,evidence),List.of());
    }
}
