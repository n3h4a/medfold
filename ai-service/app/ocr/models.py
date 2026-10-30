from enum import Enum

from pydantic import BaseModel, Field


class FileKind(str, Enum):
    PDF = "PDF"
    IMAGE = "IMAGE"


class TextSource(str, Enum):
    PDF_TEXT = "PDF_TEXT"
    """Text read from the PDF's own text layer (exact)."""
    NONE = "NONE"
    """No text found yet; the page needs OCR."""


class PageText(BaseModel):
    page_number: int = Field(alias="pageNumber", ge=1)
    text: str
    source: TextSource
    char_count: int = Field(alias="charCount", ge=0)

    model_config = {"populate_by_name": True, "serialize_by_alias": True}


class OcrResponse(BaseModel):
    file_kind: FileKind = Field(alias="fileKind")
    page_count: int = Field(alias="pageCount", ge=1)
    pages: list[PageText]
    pages_needing_ocr: list[int] = Field(alias="pagesNeedingOcr")

    model_config = {"populate_by_name": True, "serialize_by_alias": True}
