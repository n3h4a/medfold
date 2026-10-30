from enum import Enum

from pydantic import BaseModel, Field


class FileKind(str, Enum):
    PDF = "PDF"
    IMAGE = "IMAGE"


class TextSource(str, Enum):
    PDF_TEXT = "PDF_TEXT"
    """Read from the PDF's own text layer (exact)."""
    TESSERACT = "TESSERACT"
    """Recognized from the page image by Tesseract."""
    CLOUD = "CLOUD"
    """Recognized by a cloud OCR service."""
    NONE = "NONE"
    """No text could be found on this page."""


class PageText(BaseModel):
    page_number: int = Field(alias="pageNumber", ge=1)
    text: str
    source: TextSource
    char_count: int = Field(alias="charCount", ge=0)
    confidence: float | None = Field(default=None, ge=0, le=1)
    """OCR word confidence from 0 to 1; null for text-layer pages (exact) and pages without text."""

    model_config = {"populate_by_name": True, "serialize_by_alias": True}


class OcrResponse(BaseModel):
    file_kind: FileKind = Field(alias="fileKind")
    page_count: int = Field(alias="pageCount", ge=1)
    pages: list[PageText]
    ocr_provider: str | None = Field(alias="ocrProvider")
    """Which OCR engine read image pages; null when every page had a text layer."""
    pages_without_text: list[int] = Field(alias="pagesWithoutText")
    """Pages where no text was found even after OCR (blank, unreadable, or over the OCR page limit)."""

    model_config = {"populate_by_name": True, "serialize_by_alias": True}
