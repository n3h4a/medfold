from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Service configuration, read from environment variables.

    When run from ai-service/, the repo-root .env is also read.
    Real environment variables always take precedence.
    """

    model_config = SettingsConfigDict(env_file=("../.env", ".env"), extra="ignore")

    ai_internal_key: str = ""
    ocr_provider: str = "tesseract"
    llm_api_key: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
