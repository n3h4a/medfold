"""Builds test PDFs in memory so tests don't depend on binary fixture files."""

import base64
import io

from pypdf import PdfReader, PdfWriter
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas

# 1x1 PNG, enough to place an image on a page with no text layer (like a scan).
TINY_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

LAB_ROWS = [
    ("Test", "Result", "Unit", "Reference range"),
    ("Haemoglobin", "11.2", "g/dL", "12.0 - 15.5"),
    ("HbA1c", "6.1", "%", "4.0 - 5.6"),
]


def _draw_lab_report(c: canvas.Canvas, title: str) -> None:
    c.setFont("Helvetica-Bold", 14)
    c.drawString(50, 800, "City Diagnostics Laboratory")
    c.setFont("Helvetica", 11)
    c.drawString(50, 780, title)
    y = 740
    for row in LAB_ROWS:
        for x, cell in zip((50, 220, 320, 420), row):
            c.drawString(x, y, cell)
        y -= 20


def _draw_scan(c: canvas.Canvas) -> None:
    c.drawImage(ImageReader(io.BytesIO(TINY_PNG)), 50, 300, width=495, height=500)


def build_pdf(*pages: str) -> bytes:
    """Each argument describes one page: 'text', 'scan', 'blank' or 'footer'."""
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=A4)
    for index, kind in enumerate(pages, start=1):
        if kind == "text":
            _draw_lab_report(c, f"Complete Blood Count - page {index}")
        elif kind == "scan":
            _draw_scan(c)
        elif kind == "footer":
            _draw_scan(c)
            c.setFont("Helvetica", 8)
            c.drawString(280, 20, f"Page {index}")
        elif kind != "blank":
            raise ValueError(kind)
        c.showPage()
    c.save()
    return buffer.getvalue()


def encrypt(pdf: bytes, password: str = "secret") -> bytes:
    writer = PdfWriter(clone_from=PdfReader(io.BytesIO(pdf)))
    writer.encrypt(password)
    out = io.BytesIO()
    writer.write(out)
    return out.getvalue()
