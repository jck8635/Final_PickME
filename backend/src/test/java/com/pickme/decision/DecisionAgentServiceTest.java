package com.pickme.decision;

import com.pickme.ai.AiClient;
import com.pickme.history.HistorySimilarService;
import com.pickme.member.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DecisionAgentServiceTest {
    private final AiClient ai = mock(AiClient.class);
    private final DecisionService decisions = mock(DecisionService.class);
    private final HistorySimilarService history = mock(HistorySimilarService.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final DecisionAgentService agent = new DecisionAgentService(ai, decisions, history, profiles, true);
    private final DecisionService.ConsultRequest req = new DecisionService.ConsultRequest(List.of(), "objective", "제목", "고민", List.of(), "", List.of(), List.of(), null);
    private final DecisionService.Prepared prepared = new DecisionService.Prepared(List.of(), "objective", "제목", "고민", "", List.of(), "");
    private final AiClient.DecisionRequest request = DecisionService.toAiRequest(prepared, null);

    @BeforeEach void setup() { when(decisions.prepare(req)).thenReturn(prepared); }
    private AiClient.AgentPlan plan(List<String> tools, List<String> missing) {
        var p = new AiClient.AgentPlan("분석", tools, missing);
        when(ai.plan(request)).thenReturn(p);
        return p;
    }
    @Test void noTools() {
        var p = plan(List.of(), List.of());
        when(ai.evaluate(request,p,Map.of())).thenReturn("결과");
        assertEquals("결과",agent.consult(req,1,false).result());
        verifyNoInteractions(history,profiles);
        var order = inOrder(ai);
        order.verify(ai).plan(request);
        order.verify(ai).evaluate(request,p,Map.of());
    }
    @Test void missingInformationStopsEvaluation() {
        plan(List.of("history"),List.of("예산은?","기간은?","목적은?","기타?"));
        var out = agent.consult(req,1,false);
        assertNull(out.result());
        assertEquals(List.of("예산은?","기간은?","목적은?"),out.questions());
        verify(ai,never()).evaluate(any(),any(),anyMap());
        verifyNoInteractions(history,profiles);
    }
    @Test void proceedUsesOnlyHistoryOnce() {
        var p = plan(List.of("history","history"),List.of("예산은?"));
        var records = new HistorySimilarService.SimilarResponse(List.of(),"과거 회고","hybrid",null);
        when(history.search(1,"고민")).thenReturn(records);
        when(ai.evaluate(request,p,Map.of("history",records))).thenReturn("결과");
        assertEquals("결과",agent.consult(req,1,true).result());
        verify(history,times(1)).search(1,"고민");
        verifyNoInteractions(profiles);
    }
    @Test void guestCannotUsePersonalTools() {
        var p = plan(List.of("profile","history"),List.of());
        when(ai.evaluate(request,p,Map.of())).thenReturn("결과");
        assertEquals("결과",agent.consult(req,null,false).result());
        verifyNoInteractions(history,profiles);
    }
    @Test void profileOnly() {
        var p = plan(List.of("profile"),List.of());
        var profile = new UserProfileService.ProfileResponse(1,2000,"서울","개발자","INTJ",List.of("운동"),List.of(),"");
        when(profiles.getProfile(1)).thenReturn(profile);
        var evidence = Map.<String,Object>of("profile",DecisionService.toAiRequest(prepared,profile).userProfile());
        when(ai.evaluate(request,p,evidence)).thenReturn("결과");
        assertEquals("결과",agent.consult(req,1,false).result());
        verifyNoInteractions(history);
    }
    @Test void toolFailureStillEvaluates() {
        var p = plan(List.of("history"),List.of());
        when(history.search(1,"고민")).thenThrow(new IllegalStateException("offline"));
        when(ai.evaluate(request,p,Map.of("history",Map.of("status","unavailable")))).thenReturn("결과");
        assertEquals("결과",agent.consult(req,1,false).result());
    }
    @Test void unknownToolIgnored() {
        var p = plan(List.of("memory"),List.of());
        when(ai.evaluate(request,p,Map.of())).thenReturn("결과");
        assertEquals("결과",agent.consult(req,1,false).result());
        verifyNoInteractions(history,profiles);
    }
}
