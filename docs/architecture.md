# Medfold architecture

Living document, updated at every 10-task checkpoint.

| Checkpoint | Tasks | Theme |
|---|---|---|
| 1 | 1–10 | Setup and authentication |
| 2 | 11–20 | Documents end to end, OCR, extraction contract |
| 3 | 21–30 | LLM extraction, processing pipeline, timeline |
| **4 (current)** | **31–40** | **Search, editing, account control, operations and security** |

---

## 1. System overview

Everything in this diagram exists and is tested.

```mermaid
flowchart LR
    subgraph Browser
        UI["React SPA<br/>Documents timeline + search · Upload queue<br/>Detail + results + editing · Test trends · Medicines"]
    end

    subgraph Backend["Spring Boot API :8080 (only public backend)"]
        SEC["Security filter chain<br/>JWT on every request"]
        AUTH["auth/"]
        DOCS["document/<br/>upload · list · get · status · file · delete<br/>reprocess · edit details"]
        RECORDS["records/<br/>results · labs · trends · medicines<br/>edit/remove rows · LabFlagCalculator"]
        SEARCH["search/<br/>every word, owner-scoped SQL"]
        ACCOUNT["account/<br/>export · delete"]
        STORE["storage/<br/>StorageService"]
        WORKER["processing/<br/>job queue + worker"]
        AICLIENT["ai/<br/>AiServiceClient"]
    end

    subgraph AI["FastAPI AI service :8000 (internal only)"]
        OCR["/v1/ocr<br/>text layer → Tesseract"]
        EXTRACT["/v1/extract<br/>LLM provider (mock · Claude)<br/>validation + safety check"]
        SCHEMA["ExtractionResult schema<br/>+ prompt extraction-v1"]
    end

    DB[("PostgreSQL 16<br/>users · documents · pages · lab_results<br/>medications · terms · jobs · extraction_runs")]
    FILES[("data/uploads/{userId}/{uuid}.ext")]
    LLM(["LLM API"])

    UI -- "Bearer token<br/>XHR upload with progress<br/>blob download for viewer" --> SEC
    SEC --> AUTH --> DB
    SEC --> DOCS --> DB
    SEC --> RECORDS --> DB
    SEC --> SEARCH --> DB
    SEC --> ACCOUNT --> DB
    DOCS --> STORE --> FILES
    DOCS -- "enqueue (same transaction)" --> DB
    WORKER -- "claim: FOR UPDATE SKIP LOCKED" --> DB
    WORKER --> STORE
    WORKER --> AICLIENT
    AICLIENT -- "X-Internal-Api-Key" --> OCR
    AICLIENT --> EXTRACT
    EXTRACT --> SCHEMA
    EXTRACT --> LLM
```

### What runs where

**Everything in Docker:** `docker compose up -d --build`, then http://localhost:3000. nginx serves the SPA and forwards `/api` to the backend (one origin, so no CORS and the `SameSite=Strict` refresh cookie works). Uploads live in the `medfold_uploads` volume, database data in `medfold_pgdata`.

**Local development:**

| Component | How to start | Port |
|---|---|---|
| PostgreSQL 16 | `docker compose up -d db` | 5432 |
| Spring Boot API | `cd backend && ./mvnw spring-boot:run` | 8080 |
| FastAPI AI service | `cd ai-service && uvicorn app.main:app --port 8000` (or `docker compose up -d --build ai-service`) | 8000 |
| React SPA | `cd frontend && npm run dev` | 5173 |

---

## 2. Document flow today

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant API as Spring Boot
    participant FS as Storage (data/uploads)
    participant DB as PostgreSQL

    B->>API: POST /documents (multipart, one file, XHR progress)
    API->>API: detect type from magic bytes (PDF/JPEG/PNG), 10 MB limit
    API->>FS: stream to temp file → atomic move, SHA-256 computed on the way
    API->>DB: duplicate? (user_id, sha256)
    alt duplicate
        API->>FS: delete stored file
        API-->>B: 409 DUPLICATE_DOCUMENT + existingDocumentId
    else new
        API->>DB: INSERT documents (status UPLOADED)
        API-->>B: 201 Location: /documents/{id}
    end

    B->>API: GET /documents/{id}/file (Bearer)
    API->>DB: findByIdAndUserId (404 if not owner)
    API-->>B: bytes, inline, Cache-Control no-store
    Note over B: shown via blob: URL, revoked on leave
```

### Ownership rules (privacy)

- **`DocumentRepository` extends `Repository`, not `JpaRepository`.** There is no `findById` or `findAll`; every read needs the owner's id.
- **Another user's document returns exactly the same 404 body as a missing one**, so ids can't be probed.
- **Storage keys are generated server-side** (`{userId}/{uuid}.{ext}`), validated, and resolved inside the storage root (no path traversal).
- **`UserIsolationTest` reads Spring's route table:**
  - every `/api/v1` endpoint must be classified (PUBLIC, SELF, COLLECTION, OWNED_BY_ID);
  - every OWNED_BY_ID endpoint is attacked by a second user, who must get 404 and change nothing;
  - verified by mutation testing: an unclassified endpoint and an owner-unaware lookup both fail the build.

---

### Processing flow

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker (every 2s)
    participant DB as PostgreSQL
    participant FS as Storage
    participant AI as AI service

    W->>DB: claim due jobs (UPDATE … FOR UPDATE SKIP LOCKED), recover stale RUNNING jobs
    W->>DB: tx 1: document → PROCESSING
    W->>FS: load stored file
    W->>AI: POST /v1/ocr (multipart)
    AI-->>W: per-page text, source, confidence
    W->>AI: POST /v1/extract (pages with text)
    AI-->>W: validated, safety-checked ExtractionResult + meta
    W->>DB: tx 2: replace pages, labs (+ flags), medicines, terms; metadata unless user-edited; extraction_runs row
    W->>DB: tx 3: job DONE, document READY
    Note over W,DB: on failure: retry after 30s, 60s, … (≤1h, 3 attempts) or FAILED with a plain message
```

No transaction is open during either AI call. The browser follows progress by polling every 3s while a document is waiting or processing.

**Failure mapping (`AiServiceClient`):**

| Cause | Retried? | User sees |
|---|---|---|
| Document problem: `PDF_ENCRYPTED`, `PDF_UNREADABLE`, `TOO_MANY_PAGES`, `UNSUPPORTED_FILE_TYPE`, `FILE_TOO_LARGE`, `IMAGE_*`, `NO_TEXT`, `TEXT_TOO_LONG`, `EXTRACTION_REFUSED` | no | the AI service's own plain-language message |
| `LLM_UNAVAILABLE`, `EXTRACTION_INVALID` | yes | the AI service's message |
| timeout, connection refused, 5xx, 429, rejected internal key, unreadable response, `AI_INTERNAL_KEY` unset | yes | a generic message |
| any other 4xx (the backend sent a bad request: a bug) | no | a generic message |
| stored file missing | no | "The original file could not be found…" |
| OCR found no text on any page | no (no model call) | "No text was found in this document" |

Internal details go to the log and `processing_jobs.last_error`, never to the user.

### Lab flags (`LabFlagCalculator`)

LOW / NORMAL / HIGH is a numeric comparison with the range printed **on the same report**, computed by the backend. It understands `a - b`, `a–b`, `a to b`, `< b`, `<= b`, `up to b`, `> a`, `>= a`, units after the numbers, `7,400` and `5,6`. Anything uncertain gets no flag: `Negative`, `<0.5`, `13.5 H`, sex/age-specific or inverted ranges. The UI words flags as "Below / Within / Above printed range".

### Search (`GET /search?q=`)

A document matches when **every word** appears somewhere in it: title, summary, doctor, facility, file name, page text, a test name or a medicine name. Matching is by substring (`ILIKE`, LIKE wildcards escaped), so "metfor" and "hba1c" work; at one person's scale this needs no full-text index. Results come in timeline order, at most 50, each with a snippet of page text around the first word and its page number. Every statement is scoped to the user, including the subqueries.

### Editing extracted data

| Endpoint | What can change |
|---|---|
| `PATCH /documents/{id}` | type, record date (can't be cleared; not in the future, not before 1900), title, doctor, facility, summary |
| `PATCH` / `DELETE /lab-results/{id}` | test name, result, unit, printed range; the flag is recomputed |
| `PATCH` / `DELETE /medications/{id}` | name, dose, frequency, route, duration, instructions |

- **PATCH semantics:** an omitted field is unchanged and an empty string clears it. That lets `{}` pass validation, so the isolation suite's attacker always reaches the ownership check and gets 404.
- **Corrections are marked `user_edited`** only when a value actually changed.
- **A new record date re-dates the document's lab results and medicines,** so trends follow the correction.
- **Reprocessing keeps corrections.** Edited document details are not overwritten. Edited lab rows and medicines are kept, and the new reading of the same test (by test key) or medicine (by name) is skipped instead of duplicated. All other rows are replaced as before.

### Trend units

The trend chart plots values in the unit of the latest result. Unit spellings count as one unit (`g/dL` = `gm/dl`, `µmol/L` = `umol/l`, `/cumm` = `/µL`). A short, explicit list of standard conversions is applied per test key: glucose, cholesterol (total/LDL/HDL), triglycerides, creatinine, uric acid, urea, haemoglobin (g/dL ↔ g/L), vitamin D, and HbA1c (% ↔ mmol/mol, IFCC–NGSP master equation). Converted points say "printed as …". Anything else is never converted and stays table-only, and tables always show values exactly as printed.

### Account control

- **`GET /account/export`** returns one JSON file (`medfold-export-v1`): the account, then for every document its details, page text (with source and confidence), lab results, medicines and terms. It is sent as an attachment with `no-store`. Original files are not embedded; each stays downloadable from its document.
- **`DELETE /account`** needs the current password. Deleting the user row cascades in one transaction to sessions, documents and everything extracted from them. Uploaded files are removed after commit (`StorageService.deleteAllForUser`); a failure there is logged, never leaving a half-deleted account. The refresh cookie is cleared.

### Password guessing (`LoginThrottle`)

Wrong passwords are counted in a 15-minute sliding window: **5 per account, 30 per client address**. Past either limit, attempts get `429 TOO_MANY_ATTEMPTS` with `Retry-After` before any password is checked. Unknown emails count exactly like real ones, so the limit reveals nothing about which accounts exist. A successful login clears the account's count, not the address's. The password confirmation for account deletion is limited the same way. Behind nginx the client address comes from `X-Forwarded-For`, trusted only from internal proxy addresses (`server.forward-headers-strategy: native`).

### Operations

- **Health:** `GET /health` also reports `aiService` (`UP`, `DOWN`, `NOT_CONFIGURED`, from `/v1/ping` with 2s timeouts, remembered for 10s) and `processing` (`ON`/`OFF`). Neither changes the overall status: without the AI service the API works and documents wait in the queue.
- **Refresh-token cleanup:** a daily job deletes tokens one day after they expire. Revoked tokens are kept until then, because presenting one again is how reuse is detected.
- **Images:** the backend is built with the Maven wrapper and runs on a JRE as a non-root user; the frontend is built with Vite and served by nginx (SPA fallback, immutable hashed assets, 11 MB upload limit).

---

## 3. AI service pipeline

```
file bytes
   │
   ▼
detect type (magic bytes) ──► 415 if not PDF/JPEG/PNG
   │
   ├── PDF ──► pypdf text layer (layout mode, per page)
   │             │  page has ≥ 25 letters/digits? ──► PDF_TEXT (exact)
   │             └─ otherwise ──► render page with pypdfium2 (~300 DPI) ─┐
   │                                                                      │
   └── image ──► Pillow: EXIF rotation ───────────────────────────────────┤
                                                                          ▼
                              prepare: grayscale · resize 1800–4000 px · autocontrast
                                                                          │
                              OcrProvider (Tesseract | Cloud placeholder) │
                              orientation fix (OSD) → words + confidence  │
                                                                          ▼
                              per-page text, source, confidence, pagesWithoutText
                                                                          │
                                                             ▼
                       prompt extraction-v1 + JSON schema ──► LLM provider (mock | anthropic)
                                                             │
                       JSON parse + ExtractionResult validation ── fail ──► one corrective retry
                                                             │
                       safety check on free text (summary, title, definitions) ── unsafe ──► retry,
                                                             │                    then sanitize
                                                             ▼
                                         ExtractResponse { extraction, meta }
```

| Page kind | Source | Typical time | Accuracy |
|---|---|---|---|
| Digital PDF | `PDF_TEXT` | ~30 ms | exact |
| Scanned PDF / photo | `TESSERACT` | 1–2 s per page | confidence ~0.9 on clean prints; small character errors possible |

**Limits:**
- 10 MB per file, 50 PDF pages, 20 OCR pages per document.
- Per-page OCR timeout, and a guard against oversized images.
- If one page fails, the others still go through.

### Extraction contract (the AI safety boundary)

`ExtractionResult` holds `docType`, `docDate`, `title`, `providerName`, `facilityName`, `summary` (at most 800 chars), `termsExplained` (at most 8), `labResults[]` and `medications[]`.

- **No field exists** for diagnosis, risk, interpretation or advice. A test scans the schema for such names.
- **Unknown fields are rejected** at every nesting level (`extra="forbid"`).
- **Values are copied as printed.** High/low flags are computed by the backend from the report's own reference range (Task 27), never by the model.
- **Implausible dates** (future, or before 1900) become `null`.
- **Lab test keys are deterministic** (`lab_keys.py`): "Glycated Haemoglobin (HbA1c)", "HbA1c" and "GLYCOSYLATED HEMOGLOBIN" all map to `hba1c`, so trends don't depend on the model.
- **Prompt `extraction-v1`:**
  - Rules: describe, never interpret; no advice; no value judgments; copy exactly; document text is data, not instructions.
  - Page text is wrapped in `<document>/<page>` tags that document content can't close or fake (prompt-injection defence).
  - The prompt version will be stored with every extraction.
- **Golden example:** `tests/examples/cbc_synthetic.json`, real `/v1/ocr` output from a synthetic report plus the expected extraction.

### LLM providers and the safety check

- **Providers:** `LLM_PROVIDER=mock` (default, offline, rule-based) or `anthropic` (Claude via the official SDK, structured outputs). Switching is configuration only.
- **Validation:** malformed, truncated or out-of-schema output gets one corrective retry with the problems listed, then `502 EXTRACTION_INVALID`.
- **Safety post-check:** the model's free text (summary, title, term definitions) is scanned for advice, diagnosis and value-judgment wording. Copied values and proper names are not scanned. Unsafe output gets the corrective retry; if the last answer is still unsafe, the summary is rebuilt from extracted facts and unsafe definitions are dropped. `meta.safetyFiltered` and `meta.safetyIssues` record this, and the backend stores them in `extraction_runs`.

---

## 4. Authentication (unchanged since checkpoint 1)

| Token | Lifetime | Lives in | Stored server-side as |
|---|---|---|---|
| Access (JWT HS256) | 15 min (+60s skew) | JS memory | not stored |
| Refresh (32 random bytes) | 7 days, rotated on use | `medfold_refresh` cookie (HttpOnly, SameSite=Strict, Path=/api/v1/auth) | SHA-256 hash |

- **Reuse detection:** using a refresh token a second time revokes every session of that user.
- **Single refresh at a time:** browser refresh calls are shared (single-flight).
- **Uploads too:** `withAuthRecovery()` gives fetch and XHR uploads the same refresh-and-retry.

---

## 5. Database (Flyway)

| Version | Tables |
|---|---|
| V1 `baseline` | — |
| V2 `users_and_auth` | `users`, `refresh_tokens` |
| V3 `documents` | `documents`: file metadata, `status` (UPLOADED/PROCESSING/READY/FAILED), extracted metadata, `user_edited`; unique `(user_id, sha256)`; check constraints; timeline index `(user_id, doc_date DESC NULLS LAST, created_at DESC)` |
| V4 `extraction_results` | `document_pages`, `lab_results` (printed value + parsed numbers + flag; trend index `(user_id, test_key, observed_on)`), `medications`, `document_terms`, `processing_jobs` (at most one PENDING/RUNNING job per document), `extraction_runs` (audit: OCR engine, model, prompt version, safety flags, raw output). All carry `user_id` and cascade on document delete |
| V5 `processing_jobs_user` | `processing_jobs.user_id`, so the worker loads documents through owner-scoped queries |

No migrations were needed for checkpoint 4: search reads existing columns, and `user_edited` already existed on documents, lab results and medicines.

---

## 6. API (current)

### Public backend (`/api/v1`)

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/health` | public | backend, database, schema version, AI service, processing on/off |
| POST | `/auth/register`, `/auth/login` | public | start session |
| POST | `/auth/refresh`, `/auth/logout` | refresh cookie | rotate / revoke |
| GET | `/auth/me` | self | current user |
| POST | `/documents` | collection | upload one file (201, 409 duplicate, 413, 415) |
| GET | `/documents?page&size` | collection | paged timeline (size ≤ 100) |
| GET | `/documents/{id}` | owned | details |
| GET | `/documents/{id}/status` | owned | lightweight polling |
| GET | `/documents/{id}/file` | owned | original file (inline, no-store, range requests) |
| DELETE | `/documents/{id}` | owned | delete row, then file after commit |
| POST | `/documents/{id}/reprocess` | owned | queue again (202); no-op while already queued |
| GET | `/documents/{id}/results` | owned | lab results, medicines, terms in document order |
| GET | `/labs` | collection | each test the user has, with its latest value |
| GET | `/labs/{testKey}` | collection | one test across reports, oldest first (404 if none) |
| GET | `/medications` | collection | all medicines, most recently prescribed first |
| PATCH | `/documents/{id}` | owned | correct details |
| PATCH, DELETE | `/lab-results/{id}` | owned | correct or remove a lab result |
| PATCH, DELETE | `/medications/{id}` | owned | correct or remove a medicine |
| GET | `/search?q=` | collection | documents containing every word, with snippets (≤ 50) |
| GET | `/account/export` | self | all of the user's data as a JSON download |
| DELETE | `/account` | self | delete the account and everything in it (password required) |

### Internal AI service (`X-Internal-Api-Key`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | OCR provider, languages, Tesseract version |
| GET | `/v1/ping` | key check |
| POST | `/v1/ocr` | per-page text with source and confidence |
| POST | `/v1/extract` | validated, safety-checked structured data from page texts |

Every error, in both services, uses `{status, code, message, fields?, details?}`.

---

## 7. Frontend

| Route | Screen |
|---|---|
| `/login`, `/register` | auth (redirect back to the requested page) |
| `/` | **Your documents**: drag-and-drop upload (one request per file, at most 3 in parallel, per-file progress and errors), a search box (results with snippets once typing pauses), and the document timeline grouped by record month (status, type, date, delete); refreshes while anything is processing |
| `/documents/:id` | **Detail**: file viewer (PDF iframe or image via authenticated `blob:` URL, revoked on leave) next to the extracted fields and summary; test results with "Below/Within/Above printed range", medicines, explained terms; follows processing; "Try again" / "Process again"; "Edit details", and Edit / Remove on every lab row and medicine ("Edited" marks corrections) |
| `/labs` | **Test results**: every test with its latest value |
| `/labs/:testKey` | **Trend**: line chart (dated numeric values in the latest unit, standard conversions labelled "printed as …", latest printed range shaded) and a table of every value as printed, linking to its report |
| `/medications` | **Medicines** as written, with their source document |
| `/account` | **Your account** (from the email in the header): download your data, delete the account |
| `/status` | system status: backend, database, AI service, document processing |

---

## 8. Tests

| Module | Command | Tests | Highlights |
|---|---|---|---|
| backend | `./mvnw test` (Docker needed) | 220 | route-driven user isolation (now also attacking another user's lab result and medicine), SKIP LOCKED race, upload → worker → stub AI service on a real port → database, AI error mapping and timeouts, 38 flag cases, search escaping and isolation, corrections surviving reprocessing, account deletion emptying every table and folder, login throttling |
| ai-service | `pytest` | 139 (7 need Tesseract) | PDFs generated in memory, real Tesseract on rendered text, schema safety scan, scripted LLM and fake SDK, 32 safety phrasings, prompt injection |
| frontend | `npm test` | 86 | upload queue concurrency, XHR token refresh, viewer states, polling until ready, results and trends, unit conversions, search, editing forms, reprocess, export and account deletion |

---

## 9. Task log

| # | Task | Commit |
|---|---|---|
| 1–10 | Setup and authentication (see checkpoint 1) | `57c6989` … `1b0d788` |
| 11 | documents table + owner-scoped repository | `34c5e87` |
| 12 | StorageService + local filesystem | `58f0bfc` |
| 13 | Upload endpoint (magic bytes, duplicates) | `8477c04` |
| 14 | List / get / status / file / delete | `3b92daa` |
| 15 | Route-driven isolation test suite | `4fbea37` |
| 16 | Upload screen + document list | `d4d91f4` |
| 17 | Detail page + authenticated file viewer | `9905383` |
| 18 | `/v1/ocr`: PDF text layer | `9b1bd8e` |
| 19 | Tesseract OCR for scans and photos | `911e2c7` |
| 20 | Extraction schema, lab keys, prompt v1 | `997b7f0` |
| 21 | LLM providers (mock, Anthropic) + `/v1/extract` with validation | `ed1f55a` |
| 22 | Safety post-check for diagnosis, advice and judgment wording | `bdd26ff` |
| 23 | Tables and entities for results, jobs and extraction runs | `183fb9d` |
| 24 | Job queue on upload + background worker | `c6e70f3` |
| 25 | `AiServiceClient` with retry/permanent error mapping | `4945d4d` |
| 26 | Real processor: OCR → extraction → stored results; processing on by default | `0af8cad` |
| 27 | Lab flags from the printed reference range | `4b18a3f` |
| 28 | Document results + reprocess endpoints | `91ec51c` |
| 29 | Frontend: follow processing, show results, try again | `72ea1fc` |
| 30 | Lab trends, medicines list, month-grouped timeline | `5e9568e` |
| 31 | Search by any word in details, text, tests and medicines | `f0bfbbc` |
| 32–33 | Backend: correct document details, lab results and medicines; corrections survive reprocessing | `3c38c0f` |
| 34 | Frontend: editing forms, Edit / Remove on results | `9785dee` |
| 35 | Refresh-token cleanup; backend and frontend in Docker Compose | `a87ea55` |
| 36 | Trend charts across units with standard conversions | `65d2551` |
| 37 | Delete account and everything in it | `470ee20` |
| 38 | Download all data as JSON | `b13a570` |
| 39 | AI service availability on the status page | `8878c8d` |
| 40 | Login and account-deletion throttling | `35ea90c` |

---

## 10. Known limitations (accepted for now)

- **OCR makes small character mistakes** on photos (e.g. "a" read as "4"). Mitigated by showing the original next to extracted data, flags only where the value parses with certainty, and letting users correct any value.
- **Unit conversion covers a fixed list of tests** (see *Trend units*); other unit changes leave values table-only. Units that are numerically identical under different names (e.g. µIU/mL and mIU/L for TSH) are not yet recognised.
- **Search is substring matching** without ranking or typo tolerance; fine for one person's records.
- **Corrected rows are matched to new readings by test key or medicine name only.** A corrected row whose name was changed to something new can sit next to a fresh reading of the original line after reprocessing.
- **Login throttling counts are in memory,** per backend instance; a restart forgives. Enough for one instance; several instances would need a shared store (e.g. the database).
- **The data export doesn't embed original files;** they're downloaded per document.
- **One worker instance processes 3 jobs per 2s cycle, one after another.** Enough for personal use; more instances can run safely (SKIP LOCKED).
- **English OCR only** (`OCR_LANGUAGES=eng`). Other Tesseract languages can be added via config and packages.
- **The AI service Docker image wasn't built in the cloud sandbox** (Debian mirror blocked there). It builds on a normal machine.
- **No CORS config,** by design: the Vite dev proxy and nginx in Docker Compose both serve the SPA and `/api` from one origin.
- **The frontend and backend images weren't built end to end in the cloud sandbox** (package registries are unreachable from Docker builds there). Their runtime stages were verified with locally built artifacts: backend migrates and serves, nginx serves the SPA and proxies `/api`.
