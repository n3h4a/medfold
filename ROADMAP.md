# Roadmap

Where Medfold could go next, in rough priority order. Open items are tracked as
[GitHub issues](https://github.com/n3h4a/medfold/issues).

## v1.1: trust and recovery

- Password reset and email verification.
- Optional multi-factor authentication (TOTP).
- Typo-tolerant, ranked search with `pg_trgm`.
- Sex- and age-specific reference ranges.

## v1.2: reading more documents

- More OCR languages (Hindi, Spanish, …) with per-account language preference.
- A cloud OCR provider option for poor photos, through the existing `OcrProvider` interface.
- More unit conversions and lab test synonyms.
- Handling of multi-report PDFs (several lab reports in one file).

## v1.3: sharing and insight

- Share a read-only summary or selected documents with a doctor via an expiring link.
- Printable "health summary" PDF before appointments.
- Medicine timeline: start and end dates across prescriptions.
- Export to FHIR.

## Platform

- Object storage (S3-compatible) behind `StorageService`.
- Metrics and tracing (OpenTelemetry), dashboards and alerts.
- `LISTEN/NOTIFY` to start processing immediately instead of polling.
- Encrypted backups pushed offsite automatically.
