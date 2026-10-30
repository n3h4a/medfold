from typing import Annotated

from fastapi import Depends

from app.config import Settings, get_settings
from app.errors import ServiceError
from app.ocr.providers.base import OcrProvider
from app.ocr.providers.cloud import CloudOcrProvider
from app.ocr.providers.tesseract import TesseractProvider


def get_ocr_provider(settings: Annotated[Settings, Depends(get_settings)]) -> OcrProvider:
    """Chooses the OCR implementation from OCR_PROVIDER. Also a FastAPI dependency, so tests can swap it."""
    name = settings.ocr_provider.lower()
    if name == "tesseract":
        return TesseractProvider(settings.ocr_languages, settings.ocr_page_timeout_seconds)
    if name == "cloud":
        return CloudOcrProvider()
    raise ServiceError(503, "OCR_UNAVAILABLE", f"Unknown OCR provider '{settings.ocr_provider}'")
