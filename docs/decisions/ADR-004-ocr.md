# ADR-004: PDF text layer first, OCR only where needed

**Status:** Accepted

## Context

Uploads are a mix of digital PDFs from labs (with an exact text layer), scanned PDFs, and phone
photos of paper documents. OCR is slow (seconds per page) and makes character errors; reading a
text layer is exact and takes milliseconds.

## Decision

Per page:

1. Read the PDF text layer (`pypdf`, layout mode, so table columns stay apart). A page with at
   least 25 letters or digits counts as digital; scanners often add a tiny text layer (a page
   number) that isn't the real content.
2. Otherwise render the page at about 300 DPI (`pypdfium2`) and run **Tesseract**: grayscale,
   resize to 1800–4000 px, autocontrast, orientation detection, then words with confidence.
3. Photos get EXIF rotation applied first and follow step 2.

Each page records its source (`PDF_TEXT`, `TESSERACT`, `NONE`) and confidence, kept in
`document_pages`. Limits: 50 PDF pages, 20 OCR pages, a per-page timeout; a failing page doesn't
fail the others. OCR sits behind an `OcrProvider` interface (Tesseract today, a cloud provider
placeholder).

## Alternatives considered

| Option | Why not (yet) |
|---|---|
| OCR every page | Slower and less accurate for the most common case, digital lab reports |
| Cloud OCR (Google Vision, AWS Textract) | Better on poor photos, but sends medical documents to another processor and costs per page; possible through the provider interface |
| Send page images straight to a multimodal LLM | Expensive per page, harder to audit, and couples reading to one model vendor |

## Consequences

- Digital PDFs are read exactly in ~30 ms; scans and photos take 1–2 s per page with possible small
  character errors.
- Mitigations for OCR errors: the original is always shown next to the extracted data, flags are
  only set when a value parses with certainty, and users can correct any value.
- English only by default; other languages need Tesseract packs and `OCR_LANGUAGES`.
