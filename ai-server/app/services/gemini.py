"""Gemini 호출 공통부.

- API 키/모델은 app.core.config 의 Settings(.env) 에서 읽습니다.
- 503(혼잡)·429(한도) 같은 일시적 오류는 짧게 한 번 더 시도합니다.
"""

import logging
import time

from google import genai
from google.genai import errors, types

from app.core.config import get_settings

logger = logging.getLogger(__name__)

# 같은 모델로 한 번 더 시도할 오류.
# 504(시간 초과)는 이미 오래 기다렸으므로 바로 다음 모델로 넘어갑니다.
TRANSIENT_STATUS = {429, 500, 503}
RETRY_DELAYS_SEC = (1.0,)

_client: genai.Client | None = None
_client_key: str | None = None


class GeminiUnavailable(RuntimeError):
    """API 키가 없거나 Gemini 호출이 끝내 실패한 경우."""


def has_api_key() -> bool:
    return bool(get_settings().gemini_api_key.strip())


def _get_client() -> genai.Client:
    global _client, _client_key
    settings = get_settings()
    api_key = settings.gemini_api_key.strip()
    if not api_key:
        raise GeminiUnavailable("GEMINI_API_KEY 가 설정되지 않았습니다.")
    if _client is None or _client_key != api_key:
        _client = genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(timeout=settings.gemini_timeout_ms),
        )
        _client_key = api_key
    return _client


def generate(
    contents: str,
    *,
    models: list[str] | None = None,
    config: types.GenerateContentConfig | None = None,
) -> str:
    """models 를 순서대로 시도해 첫 성공 응답의 text 를 돌려줍니다."""
    client = _get_client()
    candidates = models or [get_settings().gemini_model]
    last_error: Exception | None = None

    for model in candidates:
        for attempt in range(len(RETRY_DELAYS_SEC) + 1):
            try:
                response = client.models.generate_content(
                    model=model, contents=contents, config=config
                )
                text = response.text or ""
                if not text.strip():
                    raise GeminiUnavailable("Gemini 응답이 비어 있습니다.")
                return text
            except errors.APIError as error:
                last_error = error
                if error.code in TRANSIENT_STATUS and attempt < len(RETRY_DELAYS_SEC):
                    time.sleep(RETRY_DELAYS_SEC[attempt])
                    continue
                logger.warning("Gemini 호출 실패 (model=%s, code=%s)", model, error.code)
                break
            except Exception as error:  # 네트워크 오류 등
                last_error = error
                logger.warning("Gemini 호출 실패 (model=%s): %s", model, type(error).__name__)
                break

    raise GeminiUnavailable("Gemini 응답을 받지 못했습니다.") from last_error
