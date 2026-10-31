# Known limitations

What Medfold doesn't do, or does imperfectly, today. Each is a deliberate trade-off for a
personal-scale v1.0; most have a planned next step in [ROADMAP.md](ROADMAP.md).

## Medical scope

- **No medical advice or interpretation.** Medfold describes documents. The "Below / Within /
  Above printed range" comparison is arithmetic against the range on the same report, not a
  clinical judgment ([ADR-007](docs/decisions/ADR-007-ai-safety-boundary.md)).
- **No hospital or lab integration.** Records come only from uploaded files; there is no
  FHIR/HL7 import, and nothing is shared with doctors.
- **Sex- and age-specific ranges** ("M: 13–17, F: 12–15") aren't parsed; such results get no
  comparison rather than a possibly wrong one.

## Reading documents

- **OCR accuracy varies.** Clean prints read well (Tesseract confidence ~0.9); blurry, skewed or
  handwritten documents produce character errors. The original is always shown next to the
  extracted data, and every value can be corrected.
- **AI summaries may need review.** Extraction can miss or misread a row, especially with the
  offline reader, which handles common table and prescription layouts only. Summaries are
  descriptive by design and checked for advice wording.
- **English OCR only** by default (`OCR_LANGUAGES=eng`); other languages need Tesseract language
  packs and configuration.
- **Limits:** 10 MB per file, 50 PDF pages, 20 OCR pages per document, password-protected PDFs
  are rejected.
- **With `LLM_PROVIDER=anthropic`, page text leaves your server** for Anthropic's API. Use the
  offline reader to keep everything local.

## Features

- **Trends convert units only for a fixed list of tests** (glucose, cholesterol, creatinine, HbA1c
  and a few more); other unit changes are listed in the table but not plotted.
- **Search is substring matching** without ranking or typo tolerance.
- **The data export doesn't embed original files;** each can be downloaded from its document.
- **No offline mode, no mobile app.** The web app works on phones but needs a connection.
- **Accounts:** no email verification, password reset by email, multi-factor authentication or
  family sharing.

## Operations

- **Single server.** The deployment guide targets one Docker host with local-disk file storage.
- **Backups** are written locally by `scripts/backup.sh`; copying them offsite, encrypted, is up to
  the operator.
- **No metrics or tracing** beyond logs and the health endpoint.
