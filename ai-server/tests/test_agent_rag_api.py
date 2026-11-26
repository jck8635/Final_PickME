import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.routers import history_rag, embedding, ai
from app.services.decision_agent import Plan
from app.services.decision_service import ConsultationError

client = TestClient(app)
RAG = {'text':'진로','candidates':[{'decisionId':1,'retrospectiveNote':'과거 회고'}]}


def test_rank_success(monkeypatch):
    monkeypatch.setattr(history_rag,'generate',lambda *a,**k:'{"ranking":[{"decisionId":1,"score":90}]}')
    response = client.post('/history/rag/rank',json=RAG)
    assert response.status_code == 200
    assert response.json()['ranking'][0]['score'] == 90


@pytest.mark.parametrize('reply',['invalid','{"ranking":[{"decisionId":2,"score":90}]}','{"ranking":[{"decisionId":1,"score":101}]}'])
def test_rank_invalid(monkeypatch,reply):
    monkeypatch.setattr(history_rag,'generate',lambda *a,**k:reply)
    assert client.post('/history/rag/rank',json=RAG).status_code == 503


def test_rank_empty_candidates():
    assert client.post('/history/rag/rank',json={'text':'진로','candidates':[]}).status_code == 422


def test_summary(monkeypatch):
    monkeypatch.setattr(history_rag,'generate',lambda *a,**k:'{"summary":"과거 회고 [기록 1]"}')
    response = client.post('/history/rag/summary',json=RAG)
    assert response.status_code == 200
    assert '[기록 1]' in response.json()['summary']


def test_summary_limit():
    assert client.post('/history/rag/summary',json={'text':'진로','candidates':[{'decisionId':i} for i in range(4)]}).status_code == 400


def test_embedding_api(monkeypatch):
    monkeypatch.setattr(embedding,'create_embedding',lambda text:[0.1]*768)
    response = client.post('/history/embedding',json={'text':'고민'})
    assert response.status_code == 200
    assert response.json()['dimension'] == 768


@pytest.mark.parametrize('error,status',[(ValueError('blank'),400),(RuntimeError('offline'),503)])
def test_embedding_api_error(monkeypatch,error,status):
    def fail(text):
        raise error
    monkeypatch.setattr(embedding,'create_embedding',fail)
    assert client.post('/history/embedding',json={'text':'고민'}).status_code == status


def test_query_api(monkeypatch):
    monkeypatch.setattr(embedding,'create_query_embedding',lambda text:[0.1]*768)
    assert client.post('/history/history/query-embedding',json={'text':'검색'}).json()['dimension'] == 768


def test_plan_api(monkeypatch):
    monkeypatch.setattr(ai,'plan',lambda req:Plan(summary='분석',tools=['history'],missing=['예산은?']))
    response = client.post('/decision/plan',json={'request':{'content':'고민'}})
    assert response.status_code == 200
    assert response.json()['missing'] == ['예산은?']


def test_evaluate_api(monkeypatch):
    def evaluate(prompt,analysis,evidence):
        assert '고민' in prompt
        assert evidence['history'] == '과거 회고'
        return '결과'
    monkeypatch.setattr(ai,'evaluate',evaluate)
    response = client.post('/decision/evaluate',json={'request':{'content':'고민'},'analysis':{'summary':'분석'},'evidence':{'history':'과거 회고'}})
    assert response.status_code == 200
    assert response.json()['result'] == '결과'


@pytest.mark.parametrize('endpoint,body',[('plan',{'request':{'content':'고민'}}),('evaluate',{'request':{'content':'고민'},'analysis':{},'evidence':{}})])
def test_agent_api_failure(monkeypatch,endpoint,body):
    def fail(*args):
        raise ConsultationError('offline')
    monkeypatch.setattr(ai,endpoint,fail)
    assert client.post('/decision/'+endpoint,json=body).status_code == 503
