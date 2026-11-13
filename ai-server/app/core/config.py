from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    gemini_api_key: str = ""
    # AI 상담(채팅) 모델
    gemini_model: str = "gemini-3.8-flash"
    # 결정 도우미: 가벼운 모델부터 순서대로 시도 (쉼표 구분)
    gemini_decision_models: str = "gemini-flash-lite-latest,gemini-flash-latest"
    gemini_timeout_ms: int = 20_000

    @property
    def chat_models(self) -> list[str]:
        # 채팅 모델이 혼잡(503 등)하면 결정 도우미용 가벼운 모델로 넘어갑니다.
        return list(dict.fromkeys([self.gemini_model, *self.decision_models]))

    @property
    def decision_models(self) -> list[str]:
        models = [m.strip() for m in self.gemini_decision_models.split(",") if m.strip()]
        # 채팅 모델을 마지막 예비 모델로 둡니다.
        if self.gemini_model not in models:
            models.append(self.gemini_model)
        return models


@lru_cache
def get_settings() -> Settings:
    return Settings()
