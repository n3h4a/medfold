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
    # Tesseract language codes joined with "+", e.g. "eng+hin". Each needs its traineddata installed.
    ocr_languages: str = "eng"
    # Upper bounds that keep one document from tying up the service.
    ocr_max_pages: int = 20
    ocr_page_timeout_seconds: int = 60
    llm_api_key: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
