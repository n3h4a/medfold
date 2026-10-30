import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.llm.anthropic_provider import AnthropicProvider
from app.llm.factory import get_llm_provider
from app.main import app
from tests.conftest import KEY
from tests.test_anthropic_provider import FakeClient, message

EXAMPLE = json.loads((Path(__file__).parent / "examples" / "cbc_synthetic.json").read_text(encoding="utf-8"))
BODY = {"pages": [{"pageNumber": n, "text": t} for n, t in EXAMPLE["pages"]]}


@pytest.fixture
def api():
    app.dependency_overrides[get_settings] = lambda: Settings(_env_file=None, ai_internal_key=KEY)
    yield TestClient(app)
    app.dependency_overrides.clear()


def post(api, body, key=KEY):
    return api.post("/v1/extract", json=body, headers={"X-Internal-Api-Key": key} if key else {})


def test_works_out_of_the_box_with_the_offline_provider(api):
    response = post(api, BODY)

    assert response.status_code == 200
    body = response.json()
    assert body["meta"]["provider"] == "mock"
    assert body["meta"]["promptVersion"] == "extraction-v1"
    assert body["meta"]["safetyFiltered"] is False and body["meta"]["safetyIssues"] == []
    extraction = body["extraction"]
    assert extraction["docType"] == "LAB_REPORT"
    assert extraction["docDate"] == "2025-03-14"
    assert extraction["labResults"][3] == {
        "testName": "HbA1c", "valueText": "6.1", "unit": "%", "referenceRangeText": "4.0 – 5.6", "pageNumber": 1, "testKey": "hba1c",
    }


def test_the_anthropic_path_produces_the_same_validated_shape(api):
    expected = EXAMPLE["expected"]
    fake = FakeClient(message(text=json.dumps(expected, ensure_ascii=False)))
    app.dependency_overrides[get_llm_provider] = lambda: AnthropicProvider(model="claude-opus-5-5", client=fake)

    body = post(api, BODY).json()

    assert body["meta"]["provider"] == "anthropic"
    assert body["meta"]["model"] == "claude-opus-5-5"
    assert body["extraction"]["summary"] == expected["summary"]
    assert [r["testKey"] for r in body["extraction"]["labResults"]] == ["haemoglobin", "wbc", "platelets", "hba1c"]
    sent = fake.beta.messages.calls[0]
    assert "City Diagnostics Laboratory" in sent["messages"][0]["content"]


def test_requires_internal_key(api):
    assert post(api, BODY, key=None).status_code == 401


def test_invalid_requests_use_the_standard_error_shape(api):
    response = post(api, {"pages": []})

    assert response.status_code == 422
    body = response.json()
    assert body["code"] == "VALIDATION_ERROR"
    assert "pages" in body["fields"]


def test_blank_document_is_rejected(api):
    response = post(api, {"pages": [{"pageNumber": 1, "text": "  "}]})

    assert response.status_code == 422
    assert response.json()["code"] == "NO_TEXT"
