import secrets
from typing import Annotated

from fastapi import Header, HTTPException, status

from app.core.config import get_settings


def verify_internal_api_key(
    x_internal_api_key: Annotated[
        str | None,
        Header(alias="X-Internal-API-Key"),
    ] = None,
) -> None:

    expected_key = get_settings().ai_internal_api_key

    # 서버 인증 설정 누락
    if not expected_key:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Internal authentication unavailable",
        )

    # 인증 키 누락 또는 불일치
    if not x_internal_api_key or not secrets.compare_digest(
        x_internal_api_key.encode("utf-8"),
        expected_key.encode("utf-8"),
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unauthorized internal request",
        )
