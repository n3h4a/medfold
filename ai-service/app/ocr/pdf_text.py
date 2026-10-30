import io
import logging

from pypdf import PdfReader
from pypdf.errors import FileNotDecryptedError, PdfReadError

from app.errors import ServiceError
from app.ocr.text_cleanup import clean_text

log = logging.getLogger(__name__)

MAX_PAGES = 50


def open_pdf(content: bytes) -> PdfReader:
    """Opens a PDF, rejecting encrypted, corrupt or oversized documents with a clear error."""
    try:
        reader = PdfReader(io.BytesIO(content))
        if reader.is_encrypted and not reader.decrypt(""):
            raise ServiceError(422, "PDF_ENCRYPTED", "This PDF is password-protected. Remove the password and upload it again")
        page_count = len(reader.pages)
    except ServiceError:
        raise
    except FileNotDecryptedError as exc:
        raise ServiceError(422, "PDF_ENCRYPTED", "This PDF is password-protected. Remove the password and upload it again") from exc
    except (PdfReadError, ValueError, KeyError, TypeError) as exc:
        log.info("Unreadable PDF: %s", exc)
        raise ServiceError(422, "PDF_UNREADABLE", "This PDF appears to be damaged and could not be read") from exc

    if page_count == 0:
        raise ServiceError(422, "PDF_UNREADABLE", "This PDF has no pages")
    if page_count > MAX_PAGES:
        raise ServiceError(422, "TOO_MANY_PAGES", f"PDFs can have at most {MAX_PAGES} pages")
    return reader


def extract_page_texts(reader: PdfReader) -> list[str]:
    """
    Reads each page's embedded text. Layout mode keeps table columns aligned, which matters for
    lab reports; plain mode is the fallback for pages layout mode can't handle.
    """
    texts: list[str] = []
    for index, page in enumerate(reader.pages, start=1):
        try:
            raw = page.extract_text(extraction_mode="layout")
        except Exception:  # pypdf raises a variety of errors on unusual pages
            log.info("Layout extraction failed on page %d, using plain mode", index)
            try:
                raw = page.extract_text()
            except Exception:
                log.info("Text extraction failed on page %d", index)
                raw = ""
        texts.append(clean_text(raw or ""))
    return texts
