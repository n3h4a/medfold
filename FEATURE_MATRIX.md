# Feature matrix

What Medfold does today, how complete each feature is, and which tests prove it.
✅ done and tested · 🟡 works, with a known limit · 🔜 planned ([roadmap](ROADMAP.md))

## Accounts and privacy

| Feature | Status | Notes | Proved by |
|---|---|---|---|
| Register, log in, log out | ✅ | BCrypt; same response time for unknown emails | `AuthenticationFlowTest`, `auth.test.tsx` |
| Stay logged in across reloads | ✅ | 15-min JWT + rotating HttpOnly refresh cookie | `RefreshFlowTest`, `client.test.ts` |
| Stolen-session detection | ✅ | Reusing a refresh token revokes every session | `RefreshFlowTest`, `scripts/security-check.sh` |
| Password-guessing protection | ✅ | 5 tries per account, 30 per address, per 15 min; shared by instances | `LoginThrottleTest`, `LoginThrottleApiTest` |
| Strict per-user isolation | ✅ | Every route attacked as a second user; new routes can't skip it | `UserIsolationTest` (25/25 routes) |
| Download all my data | ✅ | One JSON file | `AccountApiTest`, `AccountPage.test.tsx` |
| Delete my account | ✅ | Password required, throttled; files removed after commit | `AccountApiTest` |
| Password reset, email verification | 🔜 | | |
| Two-factor login | 🔜 | | |

## Getting documents in

| Feature | Status | Notes | Proved by |
|---|---|---|---|
| Upload PDF, JPG, PNG (drag and drop, several at once) | ✅ | 10 MB each, 3 in parallel, per-file progress | `DocumentUploadTest`, `DocumentsPage.test.tsx` |
| Reject disguised files | ✅ | Type read from the bytes, not the name | `FileTypeDetectorTest`, `security-check.sh` |
| Detect duplicates | ✅ | SHA-256 per user, including simultaneous uploads | `DocumentUploadTest` |
| Background processing with retries | ✅ | PostgreSQL queue, backoff, stale-job recovery, safe with several instances | `JobProcessingTest` |
| Read digital PDFs exactly | ✅ | Text layer, no OCR | `test_ocr_pdf_text.py` |
| Read scans and phone photos (OCR) | 🟡 | Tesseract; English by default; handwriting reads poorly | `test_tesseract.py`, CI end-to-end photo upload |
| Extract type, date, doctor, facility, tests, medicines, terms | 🟡 | Claude or the offline reader; the offline reader knows common layouts only | `test_extraction_service.py`, `DocumentPipelineTest` |
| Process a document again | ✅ | Keeps the user's corrections | `DocumentPipelineTest`, `RecordsApiTest` |

## Understanding the record

| Feature | Status | Notes | Proved by |
|---|---|---|---|
| Overview dashboard | ✅ | Counts, results outside the printed range with sparklines, recent documents, latest prescription | `OverviewPage.test.tsx` |
| Timeline by record date | ✅ | Grouped by month, filter by type | `DocumentsPage.test.tsx` |
| Original next to extracted data | ✅ | PDF viewer or image, page text | `DocumentDetailPage.test.tsx` |
| Compare each value with the printed range | 🟡 | Arithmetic, never AI; sex/age-specific ranges get no comparison | `LabFlagCalculatorTest` |
| One test across reports (trend chart) | 🟡 | Unit conversion for common tests only | `LabsPages.test.tsx`, `units.test.ts` |
| Previous value next to the latest | ✅ | In the test list and the visit summary | `RecordsApiTest`, `LabsPages.test.tsx` |
| Medicines across all prescriptions | ✅ | | `LabsPages.test.tsx` |
| Plain-language term definitions | ✅ | Descriptive only; advice wording blocked | `test_safety.py` |
| Correct or remove anything misread | ✅ | Survives reprocessing | `EditingApiTest`, `DocumentDetailPage.test.tsx` |

## Finding and sharing

| Feature | Status | Notes | Proved by |
|---|---|---|---|
| Search any word in any document | 🟡 | Substring match with snippets; no typo tolerance or ranking | `SearchApiTest`, `DocumentsPage.test.tsx` |
| Search from anywhere (Ctrl/⌘ K) | ✅ | Pages, tests and documents, keyboard driven | `CommandPalette.test.tsx` |
| Visit summary for an appointment | ✅ | One printable page: latest results, ranges, previous values, medicines, sources | `VisitSummaryPage.test.tsx` |
| Share with a doctor by link | 🔜 | | |
| FHIR export | 🔜 | | |

## Experience and operations

| Feature | Status | Notes | Proved by |
|---|---|---|---|
| Works on phones | ✅ | Responsive layout | Screenshots in `demo/screenshots` |
| Dark mode | ✅ | Follows the system, remembers a choice | `CommandPalette.test.tsx` (theme) |
| Keyboard and screen-reader friendly | 🟡 | Labels and roles tested; no audit with assistive technology yet | UI tests query by role and label |
| One-command local setup | ✅ | `docker compose up`, or Codespaces | CI end-to-end job |
| HTTPS production setup | ✅ | Caddy, strict CSP, required secrets | `deployment-config` CI job, `security-check.sh` |
| Backups and restore | 🟡 | Local only; offsite copy is up to the operator | Manual checklist |
| Metrics and tracing | 🔜 | Logs and a health endpoint today | |
