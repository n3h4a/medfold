from app.ocr.file_type import detect_file_kind
from app.ocr.models import FileKind, OcrResponse, PageText, TextSource
from app.ocr.pdf_text import extract_page_texts, open_pdf
from app.ocr.text_cleanup import meaningful_char_count

# A page with fewer letters/digits than this is treated as scanned: scanners often add a tiny
# text layer (a page number or footer) that isn't the document's real content.
MIN_TEXT_LAYER_CHARS = 25


def extract_text(content: bytes) -> OcrResponse:
    """
    Gets the text of every page. Digital PDFs are read from their embedded text layer, which is
    exact and fast. Pages without enough text (scans, photos) are returned empty and listed in
    pagesNeedingOcr; the OCR fallback fills them in.
    """
    kind = detect_file_kind(content)

    if kind == FileKind.IMAGE:
        pages = [PageText(page_number=1, text="", source=TextSource.NONE, char_count=0)]
    else:
        pages = []
        for number, text in enumerate(extract_page_texts(open_pdf(content)), start=1):
            count = meaningful_char_count(text)
            has_text = count >= MIN_TEXT_LAYER_CHARS
            pages.append(
                PageText(
                    page_number=number,
                    text=text if has_text else "",
                    source=TextSource.PDF_TEXT if has_text else TextSource.NONE,
                    char_count=count if has_text else 0,
                )
            )

    return OcrResponse(
        file_kind=kind,
        page_count=len(pages),
        pages=pages,
        pages_needing_ocr=[p.page_number for p in pages if p.source == TextSource.NONE],
    )
