"""PickMe AI 서버 (LLM / 머신러닝 / 딥러닝 전용).

React 는 이 서버를 직접 호출하지 않습니다. Spring Boot 만 호출합니다.
  React(5173) -> Spring Boot(8080) -> FastAPI(8000)

- POST /chat              AI 상담 (고민 분석 + 선택지 추출)
- POST /decision/consult  결정 도우미 (선택지/기준 기반 조언)
"""

import logging

from fastapi import FastAPI

from app.routers import ai, health

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="PickMe AI Server", version="0.2.0")

app.include_router(health.router)
app.include_router(ai.router)
