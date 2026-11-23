
"""Gemini 임베딩 생성 서비스."""

from google.genai import types

from app.services.gemini import _get_client

EMBEDDING_MODEL = "gemini-embedding-001"
EMBEDDING_DIMENSION = 768


def create_embedding(text: str) -> list[float]:
    """입력 문장을 768차원 임베딩으로 변환한다."""
    content = text.strip()

    if not content:
        raise ValueError("임베딩할 텍스트가 비어 있습니다.")

    client = _get_client()

    response = client.models.embed_content(
        model=EMBEDDING_MODEL,
        contents=content,
        config=types.EmbedContentConfig(
            task_type="RETRIEVAL_DOCUMENT",
            output_dimensionality=EMBEDDING_DIMENSION,
        ),
    )

    if not response.embeddings:
        raise RuntimeError("Gemini 임베딩 응답이 비어 있습니다.")

    values = response.embeddings[0].values

    if values is None or len(values) != EMBEDDING_DIMENSION:
        raise RuntimeError("임베딩 차원이 올바르지 않습니다.")

    return list(values)


def create_query_embedding(text: str) -> list[float]:
    from google import genai
    from google.genai import types
    import os

    client = genai.Client(
        api_key=os.environ["GEMINI_API_KEY"]
    )

    response = client.models.embed_content(
        model="gemini-embedding-001",
        contents=text,
        config=types.EmbedContentConfig(
            task_type="RETRIEVAL_QUERY",
            output_dimensionality=768,
        ),
    )

    if not response.embeddings:
        raise RuntimeError("임베딩 생성 결과가 비어 있습니다.")

    vector = response.embeddings[0].values

    if vector is None or len(vector) != 768:
        raise RuntimeError("임베딩 차원이 768이 아닙니다.")

    return vector
