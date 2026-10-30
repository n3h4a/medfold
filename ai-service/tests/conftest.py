import shutil

import pytest
from PIL import Image

from app.config import Settings, get_settings
from app.main import app
from app.ocr.models import TextSource
from app.ocr.providers.base import OcrPageError, OcrProvider, RecognizedPage
from app.ocr.providers.factory import get_ocr_provider

KEY = "test-internal-key"

requires_tesseract = pytest.mark.skipif(shutil.which("tesseract") is None, reason="Tesseract is not installed")


class FakeOcrProvider(OcrProvider):
    """Returns scripted text per call, so routing logic can be tested without Tesseract."""

    source = TextSource.TESSERACT

    def __init__(self, results: list[str | Exception] | None = None) -> None:
        self.results = list(results or [])
        self.calls: list[Image.Image] = []

    def recognize(self, image: Image.Image) -> RecognizedPage:
        self.calls.append(image)
        result = self.results.pop(0) if self.results else f"Recognized text from scanned page {len(self.calls)}"
        if isinstance(result, Exception):
            raise result
        return RecognizedPage(text=result, confidence=0.91)


@pytest.fixture
def settings() -> Settings:
    return Settings(_env_file=None, ai_internal_key=KEY)


@pytest.fixture
def fake_ocr() -> FakeOcrProvider:
    return FakeOcrProvider()


@pytest.fixture
def client(settings, fake_ocr):
    from fastapi.testclient import TestClient

    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[get_ocr_provider] = lambda: fake_ocr
    yield TestClient(app)
    app.dependency_overrides.clear()


@pytest.fixture
def real_ocr_client(settings):
    """Uses the real provider chosen from settings (Tesseract)."""
    from fastapi.testclient import TestClient

    app.dependency_overrides[get_settings] = lambda: settings
    yield TestClient(app)
    app.dependency_overrides.clear()


def post_ocr(client, content: bytes, name: str = "doc.pdf", key: str | None = KEY):
    headers = {"X-Internal-Api-Key": key} if key else {}
    return client.post("/v1/ocr", headers=headers, files={"file": (name, content, "application/octet-stream")})


__all__ = ["FakeOcrProvider", "OcrPageError", "post_ocr", "requires_tesseract", "KEY"]
