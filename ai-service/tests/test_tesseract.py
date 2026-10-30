"""Real OCR with Tesseract on rendered text. Skipped where Tesseract isn't installed."""

from PIL import Image

from app.ocr.images import load_image, prepare_for_ocr
from tests.conftest import post_ocr, requires_tesseract
from tests.image_factory import jpeg_with_exif_rotation, scanned_pdf, text_image, to_bytes
from tests.pdf_factory import build_pdf

pytestmark = requires_tesseract


def assert_reads_prescription(text: str) -> None:
    assert "Sunrise Clinic" in text
    assert "Metformin 500 mg" in text
    assert "Vitamin D3" in text


def test_reads_a_photo_of_a_prescription(real_ocr_client):
    body = post_ocr(real_ocr_client, to_bytes(text_image()), name="rx.png").json()

    page = body["pages"][0]
    assert page["source"] == "TESSERACT"
    assert_reads_prescription(page["text"])
    assert page["confidence"] > 0.8
    assert body["ocrProvider"] == "TESSERACT"


def test_keeps_lines_separate(real_ocr_client):
    text = post_ocr(real_ocr_client, to_bytes(text_image()), name="rx.png").json()["pages"][0]["text"]

    lines = [line for line in text.splitlines() if line.strip()]
    assert any(line.startswith("Metformin") for line in lines)
    assert any(line.startswith("Vitamin") for line in lines)


def test_reads_a_sideways_photo(real_ocr_client):
    sideways = text_image().rotate(90, expand=True)

    text = post_ocr(real_ocr_client, to_bytes(sideways), name="rx.png").json()["pages"][0]["text"]

    assert_reads_prescription(text)


def test_respects_exif_rotation_from_phone_cameras(real_ocr_client):
    photo = jpeg_with_exif_rotation(text_image())

    upright = load_image(photo)
    assert upright.width > upright.height
    assert_reads_prescription(post_ocr(real_ocr_client, photo, name="rx.jpg").json()["pages"][0]["text"])


def test_reads_scanned_pages_inside_a_pdf_next_to_digital_ones(real_ocr_client):
    digital_then_scanned = build_pdf("text")  # page 1: real text layer
    scan = scanned_pdf(text_image())

    body_digital = post_ocr(real_ocr_client, digital_then_scanned).json()
    body_scan = post_ocr(real_ocr_client, scan).json()

    assert body_digital["pages"][0]["source"] == "PDF_TEXT"
    assert body_scan["pages"][0]["source"] == "TESSERACT"
    assert_reads_prescription(body_scan["pages"][0]["text"])


def test_blank_image_has_no_text(real_ocr_client):
    blank = Image.new("RGB", (1200, 1600), "white")

    body = post_ocr(real_ocr_client, to_bytes(blank), name="blank.png").json()

    assert body["pagesWithoutText"] == [1]


def test_small_images_are_enlarged_and_huge_ones_reduced():
    assert max(prepare_for_ocr(Image.new("RGB", (600, 400), "white")).size) == 1800
    assert max(prepare_for_ocr(Image.new("RGB", (6000, 3000), "white")).size) == 4000
    assert prepare_for_ocr(Image.new("RGB", (2500, 1800), "white")).size == (2500, 1800)
