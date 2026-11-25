"""결정 기록 임베딩 API."""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.embedding_service import create_embedding, create_query_embedding

router = APIRouter(prefix="/history", tags=["history-ai"])


class QueryEmbeddingRequest(BaseModel):
    text: str = Field(min_length=1, max_length=5000)


@router.post("/history/query-embedding")
def query_embedding(request: QueryEmbeddingRequest):
    vector = create_query_embedding(request.text)

    return {
        "model": "gemini-embedding-001",
        "dimension": len(vector),
        "embedding": vector,
    }


class EmbeddingRequest(BaseModel):
    text: str = Field(min_length=1, max_length=10000)


class EmbeddingResponse(BaseModel):
    model: str
    dimension: int
    embedding: list[float]


@router.post("/embedding", response_model=EmbeddingResponse)
def generate_embedding(req: EmbeddingRequest):
    try:
        vector = create_embedding(req.text)
        return EmbeddingResponse(
            model="gemini-embedding-001",
            dimension=len(vector),
            embedding=vector,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from None
    except Exception:
        raise HTTPException(
            status_code=503,
            detail="임베딩 생성에 실패했습니다.",
        ) from None
