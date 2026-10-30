"""Which pages get OCR'd and how results are combined, using a fake OCR provider."""

from app.ocr.providers.base import OcrPageError
from tests.conftest import post_ocr
from tests.image_factory import text_image, to_bytes
from tests.pdf_factory import build_pdf


def test_only_pages_without_a_text_layer_are_ocrd(client, fake_ocr):
    body = post_ocr(client, build_pdf("text", "scan", "footer")).json()

    assert len(fake_ocr.calls) == 2
    assert [p["source"] for p in body["pages"]] == ["PDF_TEXT", "TESSERACT", "TESSERACT"]
    assert body["pages"][1]["text"] == "Recognized text from scanned page 1"
    assert body["pages"][1]["confidence"] == 0.91
    assert body["ocrProvider"] == "TESSERACT"
    assert body["pagesWithoutText"] == []


def test_pages_are_rendered_large_and_grayscale_for_ocr(client, fake_ocr):
    post_ocr(client, build_pdf("scan"))

    image = fake_ocr.calls[0]
    assert image.mode == "L"
    assert max(image.size) >= 1800


def test_blank_results_leave_the_page_without_text(client, fake_ocr):
    fake_ocr.results = ["", " | ~ "]

    body = post_ocr(client, build_pdf("blank", "scan")).json()

    assert body["pagesWithoutText"] == [1, 2]
    assert all(p["source"] == "NONE" for p in body["pages"])


def test_a_failing_page_does_not_stop_the_others(client, fake_ocr):
    fake_ocr.results = [OcrPageError("timed out"), "Discharge summary from City Hospital"]

    body = post_ocr(client, build_pdf("scan", "scan")).json()

    assert body["pages"][0]["source"] == "NONE"
    assert body["pages"][1]["text"] == "Discharge summary from City Hospital"
    assert body["pagesWithoutText"] == [1]


def test_ocr_is_capped_per_document(client, fake_ocr, settings):
    settings.ocr_max_pages = 2

    body = post_ocr(client, build_pdf("scan", "scan", "scan")).json()

    assert len(fake_ocr.calls) == 2
    assert body["pagesWithoutText"] == [3]


def test_images_are_ocrd_as_a_single_page(client, fake_ocr):
    body = post_ocr(client, to_bytes(text_image(), "JPEG"), name="photo.jpg").json()

    assert len(fake_ocr.calls) == 1
    assert body["fileKind"] == "IMAGE"
    assert body["pages"][0]["source"] == "TESSERACT"


def test_damaged_image_is_rejected(client):
    response = post_ocr(client, b"\x89PNG\r\n\x1a\n" + b"garbage" * 10, name="photo.png")

    assert response.status_code == 422
    assert response.json()["code"] == "IMAGE_UNREADABLE"


def test_unknown_or_unconfigured_provider_is_reported(real_ocr_client, settings):
    settings.ocr_provider = "cloud"
    response = post_ocr(real_ocr_client, build_pdf("scan"))
    assert response.status_code == 503
    assert response.json()["code"] == "OCR_UNAVAILABLE"

    settings.ocr_provider = "magic"
    assert post_ocr(real_ocr_client, build_pdf("scan")).json()["code"] == "OCR_UNAVAILABLE"
