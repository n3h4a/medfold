import shutil
import subprocess
from typing import Annotated

from fastapi import APIRouter, Depends

from app.config import Settings, get_settings
from app.security import require_internal_key

router = APIRouter()


def tesseract_version() -> str | None:
    """Return the installed Tesseract version, or None if it is not installed."""
    if shutil.which("tesseract") is None:
        return None
    try:
        result = subprocess.run(
            ["tesseract", "--version"], capture_output=True, text=True, timeout=5
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    output = (result.stdout or result.stderr).strip()
    first_line = output.splitlines()[0] if output else ""
    return first_line.removeprefix("tesseract ").strip() or None


@router.get("/health")
def health(settings: Annotated[Settings, Depends(get_settings)]) -> dict:
    version = tesseract_version()
    return {
        "service": "medfold-ai-service",
        "status": "UP",
        "ocrProvider": settings.ocr_provider,
        "ocrLanguages": settings.ocr_languages,
        "tesseractAvailable": version is not None,
        "tesseractVersion": version,
        "internalKeyConfigured": bool(settings.ai_internal_key),
        "llmConfigured": bool(settings.llm_api_key),
    }


@router.get("/v1/ping", dependencies=[Depends(require_internal_key)])
def ping() -> dict:
    """Lets the backend verify that its internal API key is accepted."""
    return {"pong": True}
