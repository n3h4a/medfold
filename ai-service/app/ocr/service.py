import logging

from app.ocr.file_type import detect_file_kind
from app.ocr.images import load_image, prepare_for_ocr, render_pdf_pages
from app.ocr.models import FileKind, OcrResponse, PageText, TextSource
from app.ocr.pdf_text import extract_page_texts, open_pdf
from app.ocr.providers.base import OcrPageError, OcrProvider
from app.ocr.text_cleanup import clean_text, meaningful_char_count

log = logging.getLogger(__name__)

# A PDF page with fewer letters/digits than this is treated as scanned: scanners often add a tiny
# text layer (a page number or footer) that isn't the document's real content.
MIN_TEXT_LAYER_CHARS = 25
# OCR on a blank page can produce a few stray characters; below this the page counts as empty.
MIN_OCR_CHARS = 10


def _empty(number: int) -> PageText:
    return PageText(page_number=number, text="", source=TextSource.NONE, char_count=0)


def extract_text(content: bytes, provider: OcrProvider, max_ocr_pages: int = 20) -> OcrResponse:
    """
    Gets the text of every page. Digital PDF pages are read from their text layer (exact, fast).
    The rest (scans, photos) go through OCR, up to max_ocr_pages per document.
    """
    kind = detect_file_kind(content)
    pages: dict[int, PageText] = {}
    images = {}

    if kind == FileKind.IMAGE:
        images = {1: load_image(content)}
        pages[1] = _empty(1)
    else:
        for number, text in enumerate(extract_page_texts(open_pdf(content)), start=1):
            count = meaningful_char_count(text)
            if count >= MIN_TEXT_LAYER_CHARS:
                pages[number] = PageText(page_number=number, text=text, source=TextSource.PDF_TEXT, char_count=count)
            else:
                pages[number] = _empty(number)
        needing_ocr = [n for n, p in pages.items() if p.source == TextSource.NONE][:max_ocr_pages]
        if needing_ocr:
            images = render_pdf_pages(content, needing_ocr)

    for number, image in images.items():
        try:
            recognized = provider.recognize(prepare_for_ocr(image))
        except OcrPageError as exc:
            log.warning("OCR failed on page %d: %s", number, exc)
            continue
        text = clean_text(recognized.text)
        count = meaningful_char_count(text)
        if count >= MIN_OCR_CHARS:
            pages[number] = PageText(
                page_number=number, text=text, source=provider.source, char_count=count, confidence=recognized.confidence
            )

    ordered = [pages[n] for n in sorted(pages)]
    return OcrResponse(
        file_kind=kind,
        page_count=len(ordered),
        pages=ordered,
        ocr_provider=provider.source.value if images else None,
        pages_without_text=[p.page_number for p in ordered if p.source == TextSource.NONE],
    )
