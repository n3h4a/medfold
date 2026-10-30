"""AnthropicProvider with a fake SDK client: request shape and error mapping, no network or key needed."""

import json
from types import SimpleNamespace

import anthropic
import httpx2
import pytest

from app.config import Settings
from app.errors import ServiceError
from app.extraction.schemas import extraction_json_schema
from app.llm.anthropic_provider import AnthropicProvider, api_compatible_schema
from app.llm.base import (
    LlmConfigurationError,
    LlmRefusalError,
    LlmRequest,
    LlmRequestError,
    LlmUnavailableError,
)
from app.llm.factory import get_llm_provider
from app.llm.mock import MockLlmProvider
from tests.llm_fakes import VALID

REQUEST = LlmRequest(system="SYSTEM PROMPT", user="USER MESSAGE", json_schema=extraction_json_schema(), max_tokens=16000)


def message(text=json.dumps(VALID), stop_reason="end_turn", stop_details=None):
    return SimpleNamespace(
        content=[SimpleNamespace(type="thinking", thinking=""), SimpleNamespace(type="text", text=text)],
        stop_reason=stop_reason,
        stop_details=stop_details,
        model="claude-opus-5-5",
        usage=SimpleNamespace(input_tokens=1200, output_tokens=300),
    )


class FakeMessages:
    def __init__(self, outcome):
        self.outcome = outcome
        self.calls = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        if isinstance(self.outcome, Exception):
            raise self.outcome
        return self.outcome


class FakeClient:
    def __init__(self, outcome=None):
        self.messages = FakeMessages(outcome if outcome is not None else message())
        self.beta = SimpleNamespace(messages=FakeMessages(outcome if outcome is not None else message()))


def provider(model="claude-opus-5-5", outcome=None, **options):
    client = FakeClient(outcome)
    return AnthropicProvider(model=model, client=client, **options), client


def _keys(node) -> set[str]:
    if isinstance(node, dict):
        return set(node) | set().union(*(_keys(v) for v in node.values()))
    if isinstance(node, list):
        return set().union(*(_keys(v) for v in node)) if node else set()
    return set()


def test_sends_structured_output_request_with_refusal_fallback_on_opus():
    llm, client = provider()

    response = llm.complete_json(REQUEST)

    assert json.loads(response.text) == VALID
    assert (response.input_tokens, response.output_tokens, response.model) == (1200, 300, "claude-opus-5-5")
    assert client.messages.calls == []
    call = client.beta.messages.calls[0]
    assert call["model"] == "claude-opus-5-5"
    assert call["max_tokens"] == 16000
    assert call["system"] == "SYSTEM PROMPT"
    assert call["messages"] == [{"role": "user", "content": "USER MESSAGE"}]
    assert call["betas"] == ["server-side-fallback-2026-07-01"] and call["fallbacks"] == "default"
    assert call["output_config"]["effort"] == "medium"
    assert call["output_config"]["format"]["type"] == "json_schema"
    assert not {"temperature", "top_p", "top_k", "thinking"} & set(call), "no sampling params; thinking left at model default"


def test_models_without_server_fallback_use_the_plain_endpoint():
    llm, client = provider(model="claude-haiku-5-5", effort="")

    llm.complete_json(REQUEST)

    assert client.beta.messages.calls == []
    call = client.messages.calls[0]
    assert call["model"] == "claude-haiku-5-5"
    assert "betas" not in call and "fallbacks" not in call
    assert "effort" not in call["output_config"]


def test_schema_sent_to_claude_has_only_supported_keywords():
    sent = api_compatible_schema(extraction_json_schema())
    keys = _keys(sent)

    assert not keys & {"minLength", "maxLength", "minimum", "maximum", "maxItems", "minItems", "default"}
    assert sent["additionalProperties"] is False
    assert sent["properties"]["labResults"]["items"]["additionalProperties"] is False
    assert "testKey" not in json.dumps(sent), "computed fields must not be requested from the model"
    assert "maxLength" in json.dumps(extraction_json_schema()), "original schema keeps its limits for validation"


def test_refusal_is_reported_with_its_category():
    llm, _ = provider(outcome=message(stop_reason="refusal", stop_details=SimpleNamespace(category="bio")))

    with pytest.raises(LlmRefusalError) as caught:
        llm.complete_json(REQUEST)
    assert caught.value.category == "bio"


def _status_error(cls, status):
    request = httpx2.Request("POST", "https://api.anthropic.com/v1/messages")
    return cls(f"status {status}", response=httpx2.Response(status, request=request), body=None)


_REQUEST = httpx2.Request("POST", "https://api.anthropic.com/v1/messages")


@pytest.mark.parametrize(
    ("sdk_error", "expected"),
    [
        (_status_error(anthropic.AuthenticationError, 401), LlmConfigurationError),
        (_status_error(anthropic.PermissionDeniedError, 403), LlmConfigurationError),
        (_status_error(anthropic.NotFoundError, 404), LlmConfigurationError),
        (_status_error(anthropic.BadRequestError, 400), LlmRequestError),
        (_status_error(anthropic.RateLimitError, 429), LlmUnavailableError),
        (_status_error(anthropic.InternalServerError, 500), LlmUnavailableError),
        (_status_error(anthropic.APIStatusError, 529), LlmUnavailableError),
        (anthropic.APIConnectionError(request=_REQUEST), LlmUnavailableError),
        (anthropic.APITimeoutError(request=_REQUEST), LlmUnavailableError),
    ],
)
def test_sdk_errors_map_to_provider_neutral_errors(sdk_error, expected):
    llm, _ = provider(outcome=sdk_error)

    with pytest.raises(expected):
        llm.complete_json(REQUEST)


def test_switching_provider_is_configuration_only():
    assert isinstance(get_llm_provider(Settings(_env_file=None)), MockLlmProvider)

    llm = get_llm_provider(Settings(_env_file=None, llm_provider="anthropic", llm_api_key="sk-test", llm_model="claude-sonnet-5-5"))
    assert isinstance(llm, AnthropicProvider)
    assert llm.model == "claude-sonnet-5-5"

    with pytest.raises(ServiceError) as caught:
        get_llm_provider(Settings(_env_file=None, llm_provider="gpt"))
    assert caught.value.code == "LLM_NOT_CONFIGURED"
