# Product

## Problem

People collect years of medical paperwork from different labs, clinics and hospitals: PDFs in
email, printouts, photos on a phone. When a doctor asks "what was your HbA1c last year?" or "which
medicines were you prescribed in March?", the answer is buried in a folder or lost. Each lab prints
results differently, units vary, and nothing shows a value over time.

## Who it's for

One person (or a family member helping them) keeping their own records: someone managing a
long-term condition with regular blood tests, or anyone who wants their history in one place
before an appointment.

## What Medfold does

**Upload once. Find everything. Understand your medical history.**

1. **Upload** PDFs or phone photos of reports, prescriptions and summaries.
2. **Read automatically:** the text layer or OCR, then structured extraction: type, date, doctor,
   facility, lab results with units and printed ranges, medicines, and plain definitions of terms.
3. **Organise** everything on a timeline by the date of the record, not the upload.
4. **Understand over time:** each test across all reports as a chart, with values compared to the
   range printed on each report, and unit conversion for common tests.
5. **Find** anything by any word: a test, a medicine, a doctor, a hospital or text on the page.
6. **Stay in control:** correct anything that was misread, download all data, delete the account.

## Non-goals

- **No medical advice or interpretation.** Medfold describes what documents say; it never
  diagnoses or judges a value ([ADR-007](../decisions/ADR-007-ai-safety-boundary.md)).
- **Not a hospital system:** no EHR integration, no sharing with providers (yet).
- **Not multi-tenant clinical software:** each account is one person's records.

## MVP scope (v1.0)

| Area | In v1.0 |
|---|---|
| Accounts | Register, log in, sessions that survive a refresh, log out, export, delete |
| Upload | PDF/JPG/PNG up to 10 MB, drag and drop, several at once with progress, duplicate detection |
| Reading | Digital PDFs exactly; scans and photos by OCR; extraction by Claude or an offline reader |
| Records | Lab results with range comparison, medicines, explained terms, corrections |
| Insight | Timeline by record month, per-test trends with unit conversion, full-text search |
| Trust | Original shown next to extracted data; descriptive summaries only; strict privacy isolation |
| Operations | Docker, HTTPS deployment, backups, CI with end-to-end tests |

What comes next is in [ROADMAP.md](../../ROADMAP.md); current limits in
[KNOWN_LIMITATIONS.md](../../KNOWN_LIMITATIONS.md).
