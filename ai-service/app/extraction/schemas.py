"""
What the language model is allowed to return. This schema is the safety boundary of the AI:

* There is deliberately no field for a diagnosis, risk, interpretation or advice.
* Unknown fields are rejected (extra="forbid"), so a model can't sneak one in.
* Values are copied as printed; "high/low" flags are computed later by the backend from the
  report's own reference range, never by the model.
"""

import datetime as dt
from enum import Enum
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, computed_field, field_validator, model_validator
from pydantic.alias_generators import to_camel

from app.extraction.lab_keys import lab_test_key

SUMMARY_MAX_CHARS = 800


def _short(max_length: int):
    return Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=max_length)]


class _Strict(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        serialize_by_alias=True,
        extra="forbid",
        str_strip_whitespace=True,
    )

    @model_validator(mode="before")
    @classmethod
    def _blank_strings_to_none(cls, data):
        """Models often answer "" or "N/A" for unknown values; treat those as missing."""
        if isinstance(data, dict):
            return {k: (None if isinstance(v, str) and v.strip().lower() in ("", "n/a", "na", "null", "none", "-") else v)
                    for k, v in data.items()}
        return data


class DocumentType(str, Enum):
    LAB_REPORT = "LAB_REPORT"
    PRESCRIPTION = "PRESCRIPTION"
    DISCHARGE_SUMMARY = "DISCHARGE_SUMMARY"
    IMAGING_REPORT = "IMAGING_REPORT"
    CONSULTATION_NOTE = "CONSULTATION_NOTE"
    OTHER = "OTHER"


class LabResult(_Strict):
    test_name: _short(255) = Field(description="Test name exactly as printed, e.g. 'Glycated Haemoglobin (HbA1c)'.")
    value_text: _short(100) = Field(description="Result exactly as printed, e.g. '6.1', '7,400', 'Negative', '<0.5'.")
    unit: _short(50) | None = Field(default=None, description="Unit as printed, e.g. 'g/dL', '%'. Null if none.")
    reference_range_text: _short(100) | None = Field(
        default=None, description="Reference range exactly as printed, e.g. '4.0 - 5.6'. Null if not printed."
    )
    page_number: int | None = Field(default=None, ge=1, description="Page the result appears on.")

    @computed_field(alias="testKey")
    @property
    def test_key(self) -> str:
        """
        Canonical key used to line up the same test across reports. Computed here, never taken from
        the model; it appears in responses but not in the schema the model is given.
        """
        return lab_test_key(self.test_name)


class Medication(_Strict):
    name: _short(255) = Field(description="Medicine name as written, including strength if part of the name.")
    dosage: _short(100) | None = Field(default=None, description="Dose as written, e.g. '500 mg'.")
    frequency: _short(100) | None = Field(default=None, description="How often, as written, e.g. '1-0-1', 'twice daily'.")
    route: _short(50) | None = Field(default=None, description="e.g. 'oral', 'topical', if written.")
    duration: _short(100) | None = Field(default=None, description="e.g. '30 days', if written.")
    instructions: _short(500) | None = Field(default=None, description="e.g. 'after food', if written.")


class TermExplanation(_Strict):
    term: _short(100) = Field(description="A medical term or abbreviation that appears in the document.")
    definition: _short(300) = Field(
        description="A neutral, general one-sentence definition. Never about this patient's health."
    )


class ExtractionResult(_Strict):
    doc_type: DocumentType = Field(description="The kind of document.")
    doc_date: dt.date | None = Field(
        default=None, description="Date of the record itself (report/visit/prescription date), YYYY-MM-DD. Null if absent."
    )
    title: _short(255) | None = Field(default=None, description="Short title, e.g. 'Complete Blood Count'.")
    provider_name: _short(255) | None = Field(default=None, description="Doctor's name as printed. Null if absent.")
    facility_name: _short(255) | None = Field(default=None, description="Hospital, clinic or lab name. Null if absent.")
    summary: _short(SUMMARY_MAX_CHARS) = Field(
        description="3-5 plain-language sentences describing what the document contains. Descriptive only."
    )
    terms_explained: list[TermExplanation] = Field(default_factory=list, max_length=8)
    lab_results: list[LabResult] = Field(default_factory=list, max_length=200)
    medications: list[Medication] = Field(default_factory=list, max_length=50)

    @field_validator("doc_date")
    @classmethod
    def _plausible_date(cls, value: dt.date | None) -> dt.date | None:
        """A future or ancient date is almost always an OCR or reading mistake; better unknown than wrong."""
        if value is None:
            return None
        if value > dt.date.today() or value.year < 1900:
            return None
        return value


def _inline_refs(schema: dict) -> dict:
    """Replaces $ref pointers with their definitions, giving the model one self-contained schema."""
    definitions = schema.pop("$defs", {})

    def resolve(node):
        if isinstance(node, dict):
            if "$ref" in node:
                return resolve(dict(definitions[node["$ref"].split("/")[-1]]))
            return {key: resolve(value) for key, value in node.items()}
        if isinstance(node, list):
            return [resolve(item) for item in node]
        return node

    return resolve(schema)


def extraction_json_schema() -> dict:
    """JSON Schema handed to the language model as the required output format."""
    return _inline_refs(ExtractionResult.model_json_schema(by_alias=True, mode="validation"))
