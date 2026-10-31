# ADR-010: Why OCR (and the PDF text layer) before the AI, not a vision model on the image

**Status:** Accepted · **Related:** [ADR-004](ADR-004-ocr.md) (how pages are read),
[ADR-007](ADR-007-ai-safety-boundary.md) (what the AI may say)

## Context

Modern multimodal models can read a photo of a lab report directly and return structured data. It
would have been simpler to send every upload straight to such a model. The question was whether
the AI should **see the image** or **read text that was extracted first**.

## Decision

**Text first, AI second.** The pipeline is:

1. **PDF text layer** (exact, milliseconds) for digital reports, which is what most labs send.
2. **Tesseract OCR** only for pages without a usable text layer (scans and photos), with a
   confidence score per page.
3. The **language model receives page text only** and returns JSON that must match a schema with
   no field for diagnosis or advice.

The extracted text is stored per page (`document_pages`) with its source (`PDF_TEXT`,
`TESSERACT`) and confidence.

## Why

| Reason | What it buys |
|---|---|
| **Exactness where it exists** | A digital PDF says "HbA1c 5.8 %" in its text layer. Reading that is exact; asking a model to re-read the rendered image can only add error. |
| **Auditable input** | The exact text the model saw is stored. When a value is wrong you can tell an OCR error from a model error, and the user can open the page text. |
| **Search for free** | The same page text powers full-text search and the snippets under each result, including for documents the model could not structure. |
| **The model is replaceable** | Text in, JSON out: the provider is swappable (`LLM_PROVIDER=mock` runs the whole app and every test offline, with no API key). |
| **Cost and privacy** | Text is a fraction of the tokens of page images, and nothing visual (photos of faces, letterheads) is sent to the model. |
| **Clear failure modes** | OCR and extraction are separate calls with separate errors: "no text found" is permanent and explained to the user, "model unavailable" is retried with backoff. |

## Alternatives considered

| Option | Why not (yet) |
|---|---|
| **Vision model on every page** | Simpler pipeline and better on messy handwriting, but slower and costlier, harder to audit, and it gives up exact text layers. |
| **Cloud OCR (AWS Textract, Google Document AI)** | Better on tables and handwriting; adds a second cloud processor of medical data and a paid dependency for local development. Kept as a provider slot (`OcrProvider`). |
| **Rules only (regex per lab format)** | Exact for known formats, useless for the next lab's layout. Used as the offline `mock` provider and for test keys, not as the main extractor. |

## Consequences

- **Good:** digital reports are read exactly, every value can be traced to page text, and the
  whole app runs offline for development, CI and demos.
- **Cost:** OCR struggles with handwriting and poor photos. A bad photo becomes noisy text, and
  the model can only be as good as that text. The UI shows the page source and lets users correct
  any value (corrections survive reprocessing).
- **Gap:** when extraction fails, the OCR text isn't kept yet, so a document whose model call
  failed isn't searchable until a retry succeeds. Saving page text before extraction is a small
  change on the [roadmap](../../../ROADMAP.md).
- **Next step:** a hybrid: send only low-confidence OCR pages to a vision model, keeping the
  text-first path for everything else (tracked on the [roadmap](../../../ROADMAP.md)).
