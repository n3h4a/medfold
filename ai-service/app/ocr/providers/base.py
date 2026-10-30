from abc import ABC, abstractmethod
from dataclasses import dataclass

from PIL import Image

from app.ocr.models import TextSource


@dataclass(frozen=True)
class RecognizedPage:
    text: str
    confidence: float | None
    """Mean word confidence from 0 to 1, or None if the provider doesn't report one."""


class OcrProvider(ABC):
    """
    Turns a page image into text. Tesseract runs locally; a cloud OCR service can be added as
    another implementation and selected with OCR_PROVIDER, without changing any caller.
    """

    source: TextSource

    @abstractmethod
    def recognize(self, image: Image.Image) -> RecognizedPage:
        """Recognizes one page. Raises OcrPageError if this page can't be processed."""


class OcrPageError(Exception):
    """OCR failed for one page (e.g. timeout). The page is left without text; other pages continue."""
