"""AI 상담 / 결정 도우미 API. Spring Boot 만 호출합니다.

오류 응답은 {"detail": "<사용자에게 보여줄 메시지>"} 형식입니다.
"""

import logging

from fastapi import APIRouter, HTTPException

from app.schemas.ai import ChatRequest, ChatResponse, DecisionRequest, DecisionResponse
from app.services.chat_service import ChatService
from app.services.decision_service import ConsultationError, build_prompt, get_ai_consultation

logger = logging.getLogger(__name__)

router = APIRouter(tags=["ai"])


@router.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest) -> ChatResponse:
    is_valid, error_message = ChatService.validate_input(req.message)
    if not is_valid:
        raise HTTPException(status_code=400, detail=error_message)

    try:
        result = ChatService.get_ai_response(req.message, req.tone)
    except Exception:
        logger.exception("AI 상담 처리 중 예외 발생")
        raise HTTPException(
            status_code=500, detail="일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
        ) from None

    is_crisis = bool(result.get("is_crisis"))
    return ChatResponse(
        reply=result.get("reply", ""),
        parsed_data=None if is_crisis else result.get("parsed_data"),
        is_crisis=is_crisis,
    )


@router.post("/decision/consult", response_model=DecisionResponse)
def consult(req: DecisionRequest) -> DecisionResponse:
    content = req.content.strip()
    if not content:
        raise HTTPException(status_code=400, detail="고민 내용을 입력해 주세요.")

    prompt = build_prompt(
        req.categories,
        req.style,
        req.title.strip(),
        content,
        req.conditions.strip(),
        options=[o.model_dump() for o in req.options if o.title.strip()],
        criteria=req.criteria,
    )
    try:
        return DecisionResponse(result=get_ai_consultation(prompt))
    except ConsultationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from None
