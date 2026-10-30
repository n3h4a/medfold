"""Claude via the official Anthropic SDK, using structured outputs (output_config.format)."""

import copy
import logging

import anthropic

from app.llm.base import (
    LlmConfigurationError,
    LlmProvider,
    LlmRefusalError,
    LlmRequest,
    LlmRequestError,
    LlmResponse,
    LlmUnavailableError,
)

log = logging.getLogger(__name__)

# Structured outputs accept a subset of JSON Schema. These keywords are removed before sending;
# the full schema (lengths, list sizes, ranges) is still enforced by our Pydantic validation.
_UNSUPPORTED_KEYWORDS = {
    "minLength", "maxLength", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
    "multipleOf", "minItems", "maxItems", "uniqueItems", "default",
}

# Models that accept server-side refusal fallback in its "default" form on the Claude API.
_FALLBACK_MODELS = {"claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5"}
_FALLBACK_BETA = "server-side-fallback-2026-07-01"


def api_compatible_schema(schema: dict) -> dict:
    """Copy of the schema without keywords structured outputs don't support."""

    def clean(node):
        if isinstance(node, dict):
            return {key: clean(value) for key, value in node.items() if key not in _UNSUPPORTED_KEYWORDS}
        if isinstance(node, list):
            return [clean(item) for item in node]
        return node

    return clean(copy.deepcopy(schema))


class AnthropicProvider(LlmProvider):
    name = "anthropic"

    def __init__(
        self,
        model: str,
        api_key: str = "",
        timeout_seconds: float = 120,
        max_retries: int = 2,
        effort: str = "medium",
        refusal_fallback: bool = True,
        client: anthropic.Anthropic | None = None,
    ) -> None:
        self.model = model
        self.effort = effort
        self.use_fallback = refusal_fallback and model in _FALLBACK_MODELS
        if client is not None:
            self._client = client
        else:
            # Without an explicit key the SDK resolves credentials itself (ANTHROPIC_API_KEY,
            # ANTHROPIC_AUTH_TOKEN or an `ant auth login` profile). It retries 408/409/429/5xx and
            # connection errors with backoff (max_retries).
            options = {"timeout": timeout_seconds, "max_retries": max_retries}
            self._client = anthropic.Anthropic(api_key=api_key, **options) if api_key else anthropic.Anthropic(**options)

    def complete_json(self, request: LlmRequest) -> LlmResponse:
        output_config = {"format": {"type": "json_schema", "schema": api_compatible_schema(request.json_schema)}}
        if self.effort:
            output_config["effort"] = self.effort
        params = {
            "model": self.model,
            "max_tokens": request.max_tokens,
            "system": request.system,
            "messages": [{"role": "user", "content": request.user}],
            "output_config": output_config,
        }
        try:
            if self.use_fallback:
                # On a safety decline the API re-runs the request on a suitable fallback model.
                response = self._client.beta.messages.create(**params, betas=[_FALLBACK_BETA], fallbacks="default")
            else:
                response = self._client.messages.create(**params)
        except (anthropic.AuthenticationError, anthropic.PermissionDeniedError) as exc:
            raise LlmConfigurationError("Anthropic credentials were rejected") from exc
        except anthropic.NotFoundError as exc:
            raise LlmConfigurationError(f"Unknown model or endpoint: {self.model}") from exc
        except anthropic.BadRequestError as exc:
            raise LlmRequestError(f"Request rejected: {exc.message}") from exc
        except anthropic.RateLimitError as exc:
            raise LlmUnavailableError("Rate limited") from exc
        except anthropic.APIStatusError as exc:
            if exc.status_code >= 500:
                raise LlmUnavailableError(f"Provider error {exc.status_code}") from exc
            raise LlmRequestError(f"Provider rejected request ({exc.status_code})") from exc
        except anthropic.APIConnectionError as exc:  # includes APITimeoutError
            raise LlmUnavailableError("Could not reach the provider") from exc

        if response.stop_reason == "refusal":
            details = getattr(response, "stop_details", None)
            raise LlmRefusalError(getattr(details, "category", None))

        # With adaptive thinking the content may start with thinking blocks; the JSON is the text block.
        text = next((block.text for block in response.content if block.type == "text"), "")
        usage = getattr(response, "usage", None)
        log.info("Claude extraction: model=%s request_id=%s stop=%s", response.model, getattr(response, "_request_id", None), response.stop_reason)
        return LlmResponse(
            text=text,
            model=response.model,
            stop_reason=response.stop_reason or "",
            input_tokens=getattr(usage, "input_tokens", None),
            output_tokens=getattr(usage, "output_tokens", None),
        )
