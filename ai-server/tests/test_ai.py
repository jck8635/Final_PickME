"""AI 상담/결정 도우미 API 테스트. Gemini 는 호출하지 않습니다(모킹)."""

import json

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import chat_service, decision_service, gemini
from app.services.chat_service import ChatService

client = TestClient(app)


@pytest.fixture
def no_api_key(monkeypatch):
    monkeypatch.setattr(gemini, "has_api_key", lambda: False)


@pytest.fixture
def fake_gemini(monkeypatch):
    calls = []

    def _install(reply):
        def fake_generate(contents, **kwargs):
            calls.append(contents)
            if isinstance(reply, Exception):
                raise reply
            return reply

        monkeypatch.setattr(gemini, "has_api_key", lambda: True)
        monkeypatch.setattr(gemini, "generate", fake_generate)
        return calls

    return _install


# ---------- 규칙 기반 로직 (원본 동작 유지 확인) ----------

@pytest.mark.parametrize(
    "message, expected",
    [
        ("짬뽕 vs 짜장면", ["짬뽕", "짜장면"]),
        ("오늘 점심으로 짬뽕과 짜장면 중 무엇을 먹을지 고민돼요.", ["짬뽕", "짜장면"]),
        ("낮잠 잘까 말까", ["낮잠 자기", "낮잠 자지 않기"]),
        ("회사를 그만둘까", ["회사 그만두기", "회사 유지하기"]),
    ],
)
def test_extract_options(message, expected):
    assert ChatService._extract_options(message) == expected


def test_crisis_detected_before_ai(fake_gemini):
    calls = fake_gemini("{}")
    res = client.post("/chat", json={"message": "너무 힘들어서 죽고싶어요"})
    assert res.status_code == 200
    body = res.json()
    assert body["is_crisis"] is True
    assert body["parsed_data"] is None
    assert "109" in body["reply"]
    assert calls == []


def test_general_chat_does_not_call_ai(fake_gemini):
    calls = fake_gemini("{}")
    res = client.post("/chat", json={"message": "안녕 반가워"})
    assert res.json()["parsed_data"] is None
    assert calls == []


def test_validation_error():
    res = client.post("/chat", json={"message": " "})
    assert res.status_code == 400
    assert res.json()["detail"] == "고민 내용을 입력해 주세요."


def test_fallback_without_api_key(no_api_key):
    res = client.post("/chat", json={"message": "짬뽕 vs 짜장면", "tone": "logic"})
    body = res.json()
    assert res.status_code == 200
    assert body["parsed_data"]["options"] == ["짬뽕", "짜장면"]
    assert body["parsed_data"]["category"] == "음식/식사"


def test_chat_uses_gemini_json(fake_gemini):
    fake_gemini(json.dumps({
        "is_crisis": False, "is_harmful": False, "is_general_chat": False,
        "reply": "둘 다 좋아요.",
        "parsed_data": {"title": "점심 메뉴", "category": "음식/식사", "options": ["짬뽕", "짜장면"]},
    }, ensure_ascii=False))
    res = client.post("/chat", json={"message": "짬뽕 vs 짜장면 뭐 먹을까?"})
    body = res.json()
    assert body["reply"] == "둘 다 좋아요."
    assert body["parsed_data"] == {"title": "점심 메뉴", "category": "음식/식사", "options": ["짬뽕", "짜장면"]}


def test_chat_falls_back_when_gemini_fails(fake_gemini):
    fake_gemini(gemini.GeminiUnavailable("down"))
    res = client.post("/chat", json={"message": "짬뽕 vs 짜장면"})
    assert res.status_code == 200
    assert res.json()["parsed_data"]["options"] == ["짬뽕", "짜장면"]


# ---------- 결정 도우미 ----------

def test_consult_builds_prompt_with_criteria(fake_gemini):
    calls = fake_gemini("짬뽕을 추천해요.")
    res = client.post("/decision/consult", json={
        "categories": ["음식/식사"], "style": "fact", "title": "점심",
        "content": "점심 메뉴 고민", "conditions": "예산 1만원",
        "options": [{"title": "짬뽕", "note": "얼큰"}, {"title": "짜장면"}],
        "criteria": "비용 (예산)(5점), 시간 효율(3점)",
    })
    assert res.status_code == 200
    assert res.json()["result"] == "짬뽕을 추천해요."
    prompt = calls[0]
    assert "1. 짬뽕 - 얼큰" in prompt
    assert "비용 (예산)(5점), 시간 효율(3점)" in prompt
    assert decision_service.STYLES["fact"] in prompt


def test_consult_requires_content(fake_gemini):
    fake_gemini("x")
    res = client.post("/decision/consult", json={"content": "  "})
    assert res.status_code == 400


def test_consult_without_key_returns_503(no_api_key):
    res = client.post("/decision/consult", json={"content": "고민"})
    assert res.status_code == 503


def test_chat_service_module_has_no_env_lookups():
    import inspect

    assert "os.environ" not in inspect.getsource(chat_service)
