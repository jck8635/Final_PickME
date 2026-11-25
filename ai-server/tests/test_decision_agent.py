import pytest
from app.schemas.ai import DecisionRequest
from app.services import decision_agent
from app.services.decision_service import ConsultationError

@pytest.mark.parametrize('tools', [[], ['profile','history']])
def test_plan_allowed_tools(monkeypatch, tools):
    import json
    monkeypatch.setattr(decision_agent.gemini,'has_api_key',lambda:True)
    monkeypatch.setattr(decision_agent.gemini,'generate',lambda *a,**kw:json.dumps({'summary':'고민','tools':tools}))
    assert decision_agent.plan(DecisionRequest(content='고민')).tools == tools

def test_reject_unknown_tool(monkeypatch):
    monkeypatch.setattr(decision_agent.gemini,'has_api_key',lambda:True)
    monkeypatch.setattr(decision_agent.gemini,'generate',lambda *a,**kw:'{"summary":"x","tools":["delete_db"]}')
    with pytest.raises(ConsultationError):
        decision_agent.plan(DecisionRequest(content='고민'))

def test_evidence_in_evaluation(monkeypatch):
    monkeypatch.setattr(decision_agent,'get_ai_consultation',lambda prompt:prompt)
    result=decision_agent.evaluate('현재 고민',{'summary':'분석'},{'history':'과거 회고'})
    assert '과거 회고' in result and '현재 고민' in result

def test_reject_memory_tool(monkeypatch):
    monkeypatch.setattr(decision_agent.gemini, 'has_api_key', lambda: True)
    monkeypatch.setattr(decision_agent.gemini, 'generate', lambda *a, **kw: '{"summary":"x","tools":["memory"]}')
    with pytest.raises(ConsultationError):
        decision_agent.plan(DecisionRequest(content='고민'))
