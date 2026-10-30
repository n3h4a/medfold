"""Renders text into images in memory, standing in for scans and phone photos."""

import io
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
]

PRESCRIPTION_LINES = [
    "Sunrise Clinic",
    "Dr. Anita Mehta",
    "Metformin 500 mg twice daily",
    "Vitamin D3 once weekly",
]


def _font(size: int) -> ImageFont.ImageFont:
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def text_image(lines: list[str] = PRESCRIPTION_LINES, font_size: int = 40, width: int = 1400) -> Image.Image:
    height = 120 + len(lines) * int(font_size * 1.8)
    image = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(image)
    font = _font(font_size)
    for i, line in enumerate(lines):
        draw.text((60, 60 + i * int(font_size * 1.8)), line, fill="black", font=font)
    return image


def to_bytes(image: Image.Image, fmt: str = "PNG", **save_args) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format=fmt, **save_args)
    return buffer.getvalue()


def jpeg_with_exif_rotation(image: Image.Image) -> bytes:
    """A photo stored sideways with an EXIF tag saying 'rotate 90° to display', as phones do."""
    stored = image.rotate(90, expand=True)  # stored on its side
    exif = Image.Exif()
    exif[0x0112] = 6  # Orientation: rotate 90 CW to view
    return to_bytes(stored, "JPEG", exif=exif, quality=95)


def scanned_pdf(*images: Image.Image) -> bytes:
    """A PDF whose pages are only pictures of text, like a scanner produces."""
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=A4)
    for image in images:
        w, h = image.size
        page_w = 515
        c.drawImage(ImageReader(image), 40, 800 - page_w * h / w, width=page_w, height=page_w * h / w)
        c.showPage()
    c.save()
    return buffer.getvalue()
