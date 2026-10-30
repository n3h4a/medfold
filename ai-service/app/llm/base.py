"""Provider-neutral interface for asking a language model for JSON that matches a schema."""

from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class LlmRequest:
    system: str
    user: str
    json_schema: dict
    """JSON Schema the response must follow."""
    max_tokens: int = 16000


@dataclass(frozen=True)
class LlmResponse:
    text: str
    """The raw JSON text returned by the model (validated by the caller, never trusted)."""
    model: str
    stop_reason: str
    input_tokens: int | None = None
    output_tokens: int | None = None


class LlmError(Exception):
    """Base class for provider failures. Messages are for logs, never shown to users verbatim."""


class LlmUnavailableError(LlmError):
    """Temporary: rate limit, overload, timeout or network. Safe to retry the whole job later."""


class LlmConfigurationError(LlmError):
    """Missing or invalid credentials, unknown model, unknown provider. Retrying won't help."""


class LlmRequestError(LlmError):
    """The provider rejected the request itself (a bug on our side). Retrying won't help."""


class LlmRefusalError(LlmError):
    """The model declined to process the content."""

    def __init__(self, category: str | None = None) -> None:
        super().__init__(f"Model refused (category={category})")
        self.category = category


class LlmProvider(ABC):
    """
    A language model behind a stable interface. The app talks only to this, so switching between
    the offline mock and a real model is a configuration change (LLM_PROVIDER), not a code change.
    """

    name: str
    model: str

    @abstractmethod
    def complete_json(self, request: LlmRequest) -> LlmResponse:
        """Returns the model's JSON answer as text. Raises an LlmError subclass on failure."""
