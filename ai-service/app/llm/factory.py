from typing import Annotated

from fastapi import Depends

from app.config import Settings, get_settings
from app.errors import ServiceError
from app.llm.base import LlmProvider
from app.llm.mock import MockLlmProvider


def get_llm_provider(settings: Annotated[Settings, Depends(get_settings)]) -> LlmProvider:
    """Chooses the language model from LLM_PROVIDER. Also a FastAPI dependency, so tests can swap it."""
    name = settings.llm_provider.lower()
    if name == "mock":
        return MockLlmProvider()
    if name == "anthropic":
        from app.llm.anthropic_provider import AnthropicProvider

        return AnthropicProvider(
            model=settings.llm_model,
            api_key=settings.llm_api_key,
            timeout_seconds=settings.llm_timeout_seconds,
            max_retries=settings.llm_max_retries,
            effort=settings.llm_effort,
            refusal_fallback=settings.llm_refusal_fallback,
        )
    raise ServiceError(503, "LLM_NOT_CONFIGURED", f"Unknown LLM provider '{settings.llm_provider}'")
