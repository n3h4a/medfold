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
    # Language model used for extraction. "mock" is an offline rule-based extractor that needs no key;
    # "anthropic" uses Claude. Switching is configuration only.
    llm_provider: str = "mock"
    llm_model: str = "claude-opus-5-5"
    # Leave empty to let the Anthropic SDK find credentials itself (ANTHROPIC_API_KEY, etc.).
    llm_api_key: str = ""
    llm_effort: str = "medium"
    llm_max_tokens: int = 16000
    llm_timeout_seconds: float = 120
    llm_max_retries: int = 2
    llm_refusal_fallback: bool = True
    # One normal attempt plus one corrective retry when the model's output fails validation.
    extraction_max_attempts: int = 2


@lru_cache
def get_settings() -> Settings:
    return Settings()
