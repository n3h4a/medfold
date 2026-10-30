import pytest

from app.errors import ServiceError
from app.extraction.models import PageInput
from app.extraction.service import MAX_TOTAL_CHARS, run_extraction
from app.llm.base import (
    LlmConfigurationError,
    LlmRefusalError,
    LlmRequestError,
    LlmResponse,
    LlmUnavailableError,
)
from tests.llm_fakes import VALID, ScriptedLlmProvider

PAGES = [PageInput(page_number=1, text="City Lab   Haemoglobin   11.2   g/dL")]


def run(provider, pages=PAGES):
    return run_extraction(pages, provider, max_attempts=2)


def expect_error(provider, status, code, pages=PAGES):
    with pytest.raises(ServiceError) as caught:
        run(provider, pages)
    assert (caught.value.status, caught.value.code) == (status, code)
    return caught.value


def test_valid_output_on_first_attempt():
    provider = ScriptedLlmProvider(VALID)

    response = run(provider)

    assert response.extraction.doc_type.value == "LAB_REPORT"
    assert response.meta.attempts == 1
    assert response.meta.prompt_version == "extraction-v1"
    assert response.meta.provider == "scripted" and response.meta.model == "scripted-model"
    assert (response.meta.input_tokens, response.meta.output_tokens) == (100, 50)
    request = provider.requests[0]
    assert "Never diagnose" in request.system
    assert '<page number="1">' in request.user
    assert request.json_schema["additionalProperties"] is False


@pytest.mark.parametrize(
    ("bad_answer", "expected_feedback"),
    [
        ("this is not json", "not valid JSON"),
        ({**VALID, "diagnosis": "anaemia"}, "diagnosis: Extra inputs are not permitted"),
        ({"docType": "LAB_REPORT"}, "summary: Field required"),
        (LlmResponse(text='{"docType": "LAB', model="m", stop_reason="max_tokens"), "cut off"),
    ],
)
def test_bad_output_gets_one_corrective_retry(bad_answer, expected_feedback):
    provider = ScriptedLlmProvider(bad_answer, VALID)

    response = run(provider)

    assert response.meta.attempts == 2
    retry_message = provider.requests[1].user
    assert "A previous answer to this request was rejected" in retry_message
    assert expected_feedback in retry_message
    assert "anaemia" not in retry_message, "feedback must not echo model output back"


def test_gives_up_after_the_retry():
    provider = ScriptedLlmProvider("nope", {**VALID, "advice": "rest"})

    error = expect_error(provider, 502, "EXTRACTION_INVALID")

    assert len(provider.requests) == 2
    assert "unexpected format" in error.message


@pytest.mark.parametrize(
    ("failure", "status", "code"),
    [
        (LlmRefusalError("bio"), 422, "EXTRACTION_REFUSED"),
        (LlmUnavailableError("rate limited"), 503, "LLM_UNAVAILABLE"),
        (LlmConfigurationError("bad key"), 503, "LLM_NOT_CONFIGURED"),
        (LlmRequestError("400"), 502, "LLM_ERROR"),
    ],
)
def test_provider_failures_map_to_clear_errors(failure, status, code):
    provider = ScriptedLlmProvider(failure)

    error = expect_error(provider, status, code)

    assert len(provider.requests) == 1, "provider failures are not retried here; the SDK and the job queue retry"
    assert "bad key" not in error.message and "rate limited" not in error.message


def test_rejects_documents_without_text():
    expect_error(ScriptedLlmProvider(), 422, "NO_TEXT", pages=[PageInput(page_number=1, text="   ")])


def test_rejects_documents_too_long_to_send_in_one_request():
    huge = [PageInput(page_number=i, text="x" * 60_000) for i in range(1, 4)]
    assert sum(len(p.text) for p in huge) > MAX_TOTAL_CHARS

    expect_error(ScriptedLlmProvider(), 413, "TEXT_TOO_LONG", pages=huge)


def test_pages_are_sent_in_order_and_empty_pages_skipped():
    provider = ScriptedLlmProvider(VALID)
    pages = [PageInput(page_number=3, text="third"), PageInput(page_number=1, text="first"), PageInput(page_number=2, text="")]

    run(provider, pages)

    message = provider.requests[0].user
    assert message.index('number="1"') < message.index('number="3"')
    assert 'number="2"' not in message
