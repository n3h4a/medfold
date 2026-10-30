import json
import logging
import time

from pydantic import ValidationError

from app.errors import ServiceError
from app.extraction.models import ExtractionMeta, ExtractResponse, PageInput
from app.extraction.prompt import PROMPT_VERSION, system_prompt, user_message
from app.extraction.safety import check_extraction, sanitize
from app.extraction.schemas import ExtractionResult, extraction_json_schema
from app.llm.base import (
    LlmConfigurationError,
    LlmProvider,
    LlmRefusalError,
    LlmRequest,
    LlmRequestError,
    LlmUnavailableError,
)

log = logging.getLogger(__name__)

# The whole document goes to the model in one request. Rather than silently cutting text (and
# extracting from half a document), longer documents are rejected with a clear error.
MAX_TOTAL_CHARS = 150_000


def _validation_feedback(error: Exception) -> str:
    """Describes what was wrong with the output, without echoing document content back."""
    if isinstance(error, json.JSONDecodeError):
        return f"The output was not valid JSON ({error.msg} at position {error.pos})."
    if isinstance(error, ValidationError):
        problems = [f"- {'.'.join(str(p) for p in e['loc']) or '(root)'}: {e['msg']}" for e in error.errors()[:10]]
        return "The output did not match the schema:\n" + "\n".join(problems)
    return str(error)


def run_extraction(
    pages: list[PageInput],
    provider: LlmProvider,
    doc_type_hint: str | None = None,
    max_tokens: int = 16000,
    max_attempts: int = 2,
) -> ExtractResponse:
    """
    Asks the model for structured data and validates it against ExtractionResult, then checks its free
    text for diagnosis, advice or value-judgment wording. Malformed, out-of-schema or unsafe output gets
    a corrective retry with the problems listed. If the last answer is valid but still unsafe, the unsafe
    text is replaced or dropped (the extracted data is kept). Only validated data leaves this function.
    """
    usable = [(p.page_number, p.text) for p in sorted(pages, key=lambda p: p.page_number) if p.text.strip()]
    if not usable:
        raise ServiceError(422, "NO_TEXT", "No text was found in this document")
    if sum(len(text) for _, text in usable) > MAX_TOTAL_CHARS:
        raise ServiceError(413, "TEXT_TOO_LONG", "This document has too much text to process in one go")

    started = time.monotonic()
    base_message = user_message(usable, doc_type_hint)
    schema = extraction_json_schema()
    feedback: str | None = None
    input_tokens = output_tokens = 0

    for attempt in range(1, max_attempts + 1):
        message = base_message
        if feedback:
            message += (
                "\n\nA previous answer to this request was rejected. "
                f"{feedback}\nReturn a corrected answer that follows the schema exactly."
            )
        try:
            response = provider.complete_json(
                LlmRequest(system=system_prompt(), user=message, json_schema=schema, max_tokens=max_tokens)
            )
        except LlmRefusalError as exc:
            log.warning("Extraction refused by model (category=%s)", exc.category)
            raise ServiceError(422, "EXTRACTION_REFUSED", "This document could not be processed automatically") from exc
        except LlmUnavailableError as exc:
            log.warning("LLM unavailable: %s", exc)
            raise ServiceError(503, "LLM_UNAVAILABLE", "The AI service is temporarily unavailable. Please try again later") from exc
        except LlmConfigurationError as exc:
            log.error("LLM misconfigured: %s", exc)
            raise ServiceError(503, "LLM_NOT_CONFIGURED", "The AI service is not configured correctly") from exc
        except LlmRequestError as exc:
            log.error("LLM request rejected: %s", exc)
            raise ServiceError(502, "LLM_ERROR", "The AI service could not process this request") from exc

        input_tokens += response.input_tokens or 0
        output_tokens += response.output_tokens or 0

        if response.stop_reason == "max_tokens":
            feedback = "The answer was cut off because it was too long. Keep the summary short and list each item once."
            log.warning("Extraction attempt %d hit max_tokens", attempt)
            continue
        try:
            result = ExtractionResult.model_validate(json.loads(response.text))
        except (json.JSONDecodeError, ValidationError) as exc:
            feedback = _validation_feedback(exc)
            log.warning("Extraction attempt %d failed validation: %s", attempt, feedback.splitlines()[0])
            continue

        issues = check_extraction(result)
        if issues and attempt < max_attempts:
            feedback = (
                "Some wording breaks the rule to describe the document and never interpret it, judge values "
                "or give advice:\n" + "\n".join(f"- {issue.describe()}" for issue in issues[:10])
            )
            log.warning("Extraction attempt %d failed the safety check: %s", attempt, [i.category.value for i in issues])
            continue
        if issues:
            log.warning("Unsafe wording after final attempt; sanitizing %s", sorted({i.field for i in issues}))
            result = sanitize(result, issues)

        return ExtractResponse(
            extraction=result,
            meta=ExtractionMeta(
                provider=provider.name,
                model=response.model,
                prompt_version=PROMPT_VERSION,
                attempts=attempt,
                duration_ms=round((time.monotonic() - started) * 1000),
                input_tokens=input_tokens or None,
                output_tokens=output_tokens or None,
                safety_filtered=bool(issues),
                safety_issues=[f"{issue.field}: {issue.category.value}" for issue in issues],
            ),
        )

    raise ServiceError(502, "EXTRACTION_INVALID", "The AI returned data in an unexpected format. Please try again")
