import datetime as dt
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.extraction.schemas import ExtractionResult, extraction_json_schema

EXAMPLE = json.loads((Path(__file__).parent / "examples" / "cbc_synthetic.json").read_text(encoding="utf-8"))


def minimal(**overrides) -> dict:
    data = {"docType": "LAB_REPORT", "summary": "This lab report lists blood test results."}
    data.update(overrides)
    return data


def test_golden_example_is_valid_and_round_trips_in_camel_case():
    result = ExtractionResult.model_validate(EXAMPLE["expected"])

    assert result.doc_date == dt.date(2025, 3, 14)
    assert [r.test_key for r in result.lab_results] == ["haemoglobin", "wbc", "platelets", "hba1c"]
    dumped = result.model_dump(mode="json")
    assert dumped["docType"] == "LAB_REPORT"
    assert dumped["labResults"][0]["referenceRangeText"] == "12.0 – 15.5"


def test_minimal_result_defaults_to_empty_lists():
    result = ExtractionResult.model_validate(minimal())

    assert result.lab_results == [] and result.medications == [] and result.terms_explained == []
    assert result.doc_date is None and result.provider_name is None


@pytest.mark.parametrize("field", ["diagnosis", "advice", "riskLevel", "interpretation"])
def test_rejects_any_field_outside_the_schema(field):
    with pytest.raises(ValidationError):
        ExtractionResult.model_validate(minimal(**{field: "Patient likely has anaemia"}))


def test_rejects_extra_fields_inside_lab_rows_too():
    row = {"testName": "Haemoglobin", "valueText": "11.2", "interpretation": "low"}

    with pytest.raises(ValidationError):
        ExtractionResult.model_validate(minimal(labResults=[row]))


def test_requires_type_and_summary_and_limits_summary_length():
    with pytest.raises(ValidationError):
        ExtractionResult.model_validate({"docType": "LAB_REPORT"})
    with pytest.raises(ValidationError):
        ExtractionResult.model_validate(minimal(summary="x" * 801))
    with pytest.raises(ValidationError):
        ExtractionResult.model_validate(minimal(docType="X_RAY"))


def test_placeholder_strings_become_null():
    result = ExtractionResult.model_validate(minimal(providerName="N/A", facilityName="  ", title="-"))

    assert result.provider_name is None and result.facility_name is None and result.title is None


def test_implausible_dates_become_unknown_but_garbage_is_rejected():
    future = (dt.date.today() + dt.timedelta(days=30)).isoformat()

    assert ExtractionResult.model_validate(minimal(docDate=future)).doc_date is None
    assert ExtractionResult.model_validate(minimal(docDate="1850-01-01")).doc_date is None
    with pytest.raises(ValidationError):
        ExtractionResult.model_validate(minimal(docDate="2025-13-45"))


def test_limits_list_sizes():
    term = {"term": "CBC", "definition": "A common blood test."}

    with pytest.raises(ValidationError):
        ExtractionResult.model_validate(minimal(termsExplained=[term] * 9))


FORBIDDEN_FIELD_WORDS = ("diagnos", "advice", "advis", "risk", "interpret", "recommend", "prognos", "abnormal", "flag", "severity")


def _property_names(node) -> list[str]:
    names = []
    if isinstance(node, dict):
        names += list(node.get("properties", {}))
        for value in node.values():
            names += _property_names(value)
    elif isinstance(node, list):
        for item in node:
            names += _property_names(item)
    return names


def test_schema_given_to_the_model_has_no_room_for_interpretation():
    schema = extraction_json_schema()
    names = _property_names(schema)

    assert "$ref" not in json.dumps(schema), "schema must be self-contained"
    assert schema["additionalProperties"] is False
    assert schema["properties"]["labResults"]["items"]["additionalProperties"] is False
    assert set(schema["required"]) == {"docType", "summary"}
    offending = [n for n in names if any(word in n.lower() for word in FORBIDDEN_FIELD_WORDS)]
    assert offending == [], f"schema fields that invite interpretation: {offending}"
