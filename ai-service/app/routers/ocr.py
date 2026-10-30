from fastapi import APIRouter, Depends, File, UploadFile
from fastapi.concurrency import run_in_threadpool

from app.errors import ServiceError
from app.ocr.models import OcrResponse
from app.ocr.service import extract_text
from app.security import require_internal_key

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_key)])

MAX_FILE_BYTES = 10 * 1024 * 1024


@router.post("/ocr", response_model=OcrResponse, response_model_by_alias=True)
async def ocr(file: UploadFile = File(...)) -> OcrResponse:
    """Returns the text of every page of a PDF or image, and which pages still need OCR."""
    content = await file.read(MAX_FILE_BYTES + 1)
    if not content:
        raise ServiceError(400, "FILE_REQUIRED", "No file was sent")
    if len(content) > MAX_FILE_BYTES:
        raise ServiceError(413, "FILE_TOO_LARGE", "File is larger than the 10 MB limit")
    # PDF parsing is CPU-bound; keep it off the event loop.
    return await run_in_threadpool(extract_text, content)
