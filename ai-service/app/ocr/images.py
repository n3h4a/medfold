import io
import logging

import pypdfium2 as pdfium
from PIL import Image, ImageOps, UnidentifiedImageError

from app.errors import ServiceError

log = logging.getLogger(__name__)

# Refuse absurd images ("decompression bombs") instead of exhausting memory.
Image.MAX_IMAGE_PIXELS = 60_000_000

OCR_DPI = 300
MAX_RENDER_SIDE = 5000
MIN_OCR_SIDE = 1800
MAX_OCR_SIDE = 4000


def load_image(content: bytes) -> Image.Image:
    """Opens an uploaded photo and applies its EXIF rotation, so sideways phone photos are upright."""
    try:
        image = Image.open(io.BytesIO(content))
        image.load()
        return ImageOps.exif_transpose(image)
    except Image.DecompressionBombError as exc:
        raise ServiceError(422, "IMAGE_TOO_LARGE", "This image is too large to process") from exc
    except (UnidentifiedImageError, OSError, SyntaxError) as exc:
        raise ServiceError(422, "IMAGE_UNREADABLE", "This image appears to be damaged and could not be read") from exc


def render_pdf_pages(content: bytes, page_numbers: list[int]) -> dict[int, Image.Image]:
    """Renders the given 1-based PDF pages to images at about 300 DPI for OCR."""
    rendered: dict[int, Image.Image] = {}
    pdf = pdfium.PdfDocument(content)
    try:
        for number in page_numbers:
            page = pdf[number - 1]
            try:
                width, height = page.get_size()
                scale = min(OCR_DPI / 72, MAX_RENDER_SIDE / max(width, height, 1))
                rendered[number] = page.render(scale=scale).to_pil()
            finally:
                page.close()
    finally:
        pdf.close()
    return rendered


def prepare_for_ocr(image: Image.Image) -> Image.Image:
    """
    Grayscale, size and contrast adjustments that help Tesseract on phone photos and low-res scans:
    text needs to be large enough to read, while huge images only slow things down.
    """
    image = image.convert("L")
    longest = max(image.size)
    if longest < MIN_OCR_SIDE or longest > MAX_OCR_SIDE:
        factor = (MIN_OCR_SIDE if longest < MIN_OCR_SIDE else MAX_OCR_SIDE) / longest
        image = image.resize((max(1, round(image.width * factor)), max(1, round(image.height * factor))), Image.LANCZOS)
    return ImageOps.autocontrast(image, cutoff=1)
