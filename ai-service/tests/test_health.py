import pytest
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app

TEST_KEY = "test-internal-key"


@pytest.fixture
def client():
    app.dependency_overrides[get_settings] = lambda: Settings(
        _env_file=None, ai_internal_key=TEST_KEY, ocr_provider="tesseract"
    )
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_health_reports_up(client):
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "medfold-ai-service"
    assert body["ocrProvider"] == "tesseract"
    assert body["internalKeyConfigured"] is True
    assert isinstance(body["tesseractAvailable"], bool)


def test_ping_accepts_correct_key(client):
    response = client.get("/v1/ping", headers={"X-Internal-Api-Key": TEST_KEY})

    assert response.status_code == 200
    assert response.json() == {"pong": True}


def test_ping_rejects_missing_key(client):
    assert client.get("/v1/ping").status_code == 401


def test_ping_rejects_wrong_key(client):
    response = client.get("/v1/ping", headers={"X-Internal-Api-Key": "wrong"})

    assert response.status_code == 401


def test_ping_rejects_everything_when_no_key_configured():
    app.dependency_overrides[get_settings] = lambda: Settings(
        _env_file=None, ai_internal_key=""
    )
    try:
        response = TestClient(app).get("/v1/ping", headers={"X-Internal-Api-Key": ""})
        assert response.status_code == 401
    finally:
        app.dependency_overrides.clear()
