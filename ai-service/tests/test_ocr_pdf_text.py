import pytest
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app
from tests.pdf_factory import TINY_PNG, build_pdf, encrypt

KEY = "test-internal-key"


@pytest.fixture
def client():
    app.dependency_overrides[get_settings] = lambda: Settings(_env_file=None, ai_internal_key=KEY)
    yield TestClient(app)
    app.dependency_overrides.clear()


def post_ocr(client, content: bytes, name: str = "doc.pdf", key: str | None = KEY):
    headers = {"X-Internal-Api-Key": key} if key else {}
    return client.post("/v1/ocr", headers=headers, files={"file": (name, content, "application/octet-stream")})


def test_reads_text_layer_of_digital_pdf_page_by_page(client):
    response = post_ocr(client, build_pdf("text", "text"))

    assert response.status_code == 200
    body = response.json()
    assert body["fileKind"] == "PDF"
    assert body["pageCount"] == 2
    assert body["pagesNeedingOcr"] == []
    first = body["pages"][0]
    assert first["pageNumber"] == 1
    assert first["source"] == "PDF_TEXT"
    assert "City Diagnostics Laboratory" in first["text"]
    assert "page 2" in body["pages"][1]["text"]
    assert first["charCount"] > 25


def test_keeps_table_columns_on_one_line(client):
    text = post_ocr(client, build_pdf("text")).json()["pages"][0]["text"]

    hba1c_line = next(line for line in text.splitlines() if "HbA1c" in line)
    assert "6.1" in hba1c_line and "4.0 - 5.6" in hba1c_line


def test_flags_scanned_and_blank_pages_for_ocr(client):
    body = post_ocr(client, build_pdf("text", "scan", "blank")).json()

    assert [p["source"] for p in body["pages"]] == ["PDF_TEXT", "NONE", "NONE"]
    assert body["pagesNeedingOcr"] == [2, 3]
    assert body["pages"][1]["text"] == ""


def test_a_page_number_alone_does_not_count_as_text(client):
    body = post_ocr(client, build_pdf("footer")).json()

    assert body["pagesNeedingOcr"] == [1]


def test_images_always_need_ocr(client):
    body = post_ocr(client, TINY_PNG, name="photo.png").json()

    assert body == {
        "fileKind": "IMAGE",
        "pageCount": 1,
        "pages": [{"pageNumber": 1, "text": "", "source": "NONE", "charCount": 0}],
        "pagesNeedingOcr": [1],
    }


def test_detects_type_from_content_not_name(client):
    assert post_ocr(client, build_pdf("text"), name="scan.png").json()["fileKind"] == "PDF"

    response = post_ocr(client, b"MZ\x90\x00 not a pdf", name="report.pdf")
    assert response.status_code == 415
    assert response.json()["code"] == "UNSUPPORTED_FILE_TYPE"


def test_rejects_password_protected_pdf(client):
    response = post_ocr(client, encrypt(build_pdf("text")))

    assert response.status_code == 422
    assert response.json()["code"] == "PDF_ENCRYPTED"


def test_rejects_damaged_pdf(client):
    response = post_ocr(client, b"%PDF-1.4\nthis is not really a pdf")

    assert response.status_code == 422
    assert response.json() == {
        "status": 422,
        "code": "PDF_UNREADABLE",
        "message": "This PDF appears to be damaged and could not be read",
    }


def test_rejects_pdfs_with_too_many_pages(client):
    response = post_ocr(client, build_pdf(*["blank"] * 51))

    assert response.status_code == 422
    assert response.json()["code"] == "TOO_MANY_PAGES"


def test_rejects_empty_and_oversized_uploads(client):
    assert post_ocr(client, b"").json()["code"] == "FILE_REQUIRED"

    huge = b"%PDF-1.4\n" + b"0" * (10 * 1024 * 1024)
    response = post_ocr(client, huge)
    assert response.status_code == 413
    assert response.json()["code"] == "FILE_TOO_LARGE"


def test_requires_internal_key(client):
    assert post_ocr(client, build_pdf("text"), key=None).status_code == 401
    assert post_ocr(client, build_pdf("text"), key="wrong").status_code == 401
