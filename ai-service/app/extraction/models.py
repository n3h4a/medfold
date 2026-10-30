from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.extraction.schemas import ExtractionResult


class _Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, serialize_by_alias=True, extra="forbid")


class PageInput(_Camel):
    page_number: int = Field(ge=1)
    text: str = Field(max_length=100_000)


class ExtractRequest(_Camel):
    pages: list[PageInput] = Field(min_length=1, max_length=50)
    doc_type_hint: str | None = Field(default=None, max_length=50)


class ExtractionMeta(_Camel):
    provider: str
    model: str
    prompt_version: str
    attempts: int
    duration_ms: int
    input_tokens: int | None = None
    output_tokens: int | None = None


class ExtractResponse(_Camel):
    extraction: ExtractionResult
    meta: ExtractionMeta
