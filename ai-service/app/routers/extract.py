from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.concurrency import run_in_threadpool

from app.config import Settings, get_settings
from app.extraction.models import ExtractRequest, ExtractResponse
from app.extraction.service import run_extraction
from app.llm.base import LlmProvider
from app.llm.factory import get_llm_provider
from app.security import require_internal_key

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_key)])


@router.post("/extract", response_model=ExtractResponse, response_model_by_alias=True)
async def extract(
    request: ExtractRequest,
    settings: Annotated[Settings, Depends(get_settings)],
    provider: Annotated[LlmProvider, Depends(get_llm_provider)],
) -> ExtractResponse:
    """Turns page texts (from /v1/ocr) into validated structured data: type, date, doctor, labs, medicines, summary."""
    return await run_in_threadpool(
        run_extraction,
        request.pages,
        provider,
        request.doc_type_hint,
        settings.llm_max_tokens,
        settings.extraction_max_attempts,
    )
