# Medfold architecture

Living document, updated at every 10-task checkpoint.

| Checkpoint | Tasks | Theme |
|---|---|---|
| 1 | 1–10 | Setup and authentication |
| **2 (current)** | **11–20** | **Documents end to end, OCR, extraction contract** |
| 3 (next) | 21–30 | LLM extraction, processing pipeline, timeline |

---

## 1. System overview

Solid boxes and arrows exist and are tested. Dashed ones are planned.

```mermaid
flowchart LR
    subgraph Browser
        UI["React SPA<br/>Login · Documents · Upload queue<br/>Detail page + file viewer"]
    end

    subgraph Backend["Spring Boot API :8080 (only public backend)"]
        SEC["Security filter chain<br/>JWT on every request"]
        AUTH["auth/"]
        DOCS["document/<br/>upload · list · get · status · file · delete"]
        STORE["storage/<br/>StorageService"]
        WORKER["processing worker<br/>(Task 24)"]
        AICLIENT["AiServiceClient<br/>(Task 25)"]
    end

    subgraph AI["FastAPI AI service :8000 (internal only)"]
        OCR["/v1/ocr<br/>text layer → Tesseract"]
        EXTRACT["/v1/extract<br/>(Task 21)"]
        SCHEMA["ExtractionResult schema<br/>+ prompt extraction-v1"]
    end

    DB[("PostgreSQL 16<br/>users · refresh_tokens · documents")]
    FILES[("data/uploads/{userId}/{uuid}.ext")]
    LLM(["LLM API"])

    UI -- "Bearer token<br/>XHR upload with progress<br/>blob download for viewer" --> SEC
    SEC --> AUTH --> DB
    SEC --> DOCS --> DB
    DOCS --> STORE --> FILES
    WORKER -.-> AICLIENT
    AICLIENT -. "X-Internal-Api-Key" .-> OCR
    AICLIENT -.-> EXTRACT
    EXTRACT -.-> SCHEMA
    EXTRACT -.-> LLM

    classDef planned stroke-dasharray: 5 5,color:#64748b
    class WORKER,AICLIENT,EXTRACT,LLM planned
```

### What runs where (local development)

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

## 3. AI service pipeline (built so far)

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
                                                   (Task 21) ▼
                       prompt extraction-v1 + JSON schema ──► LLM ──► ExtractionResult (validated)
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
- **Golden example:** `tests/examples/cbc_synthetic.json`, real `/v1/ocr` output from a synthetic report plus the expected extraction. Task 21 measures the model against it.

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

---

## 6. API (current)

### Public backend (`/api/v1`)

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/health` | public | backend, database, schema version |
| POST | `/auth/register`, `/auth/login` | public | start session |
| POST | `/auth/refresh`, `/auth/logout` | refresh cookie | rotate / revoke |
| GET | `/auth/me` | self | current user |
| POST | `/documents` | collection | upload one file (201, 409 duplicate, 413, 415) |
| GET | `/documents?page&size` | collection | paged timeline (size ≤ 100) |
| GET | `/documents/{id}` | owned | details |
| GET | `/documents/{id}/status` | owned | lightweight polling |
| GET | `/documents/{id}/file` | owned | original file (inline, no-store, range requests) |
| DELETE | `/documents/{id}` | owned | delete row, then file after commit |

### Internal AI service (`X-Internal-Api-Key`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | OCR provider, languages, Tesseract version |
| GET | `/v1/ping` | key check |
| POST | `/v1/ocr` | per-page text with source and confidence |

Every error, in both services, uses `{status, code, message, fields?, details?}`.

---

## 7. Frontend

| Route | Screen |
|---|---|
| `/login`, `/register` | auth (redirect back to the requested page) |
| `/` | **Your documents**: drag-and-drop upload (one request per file, at most 3 in parallel, per-file progress and errors) and the document list (status, type, date, delete) |
| `/documents/:id` | **Detail**: file viewer (PDF iframe or image via authenticated `blob:` URL, revoked on leave) next to the extracted fields and summary |
| `/status` | system status |

---

## 8. Tests

| Module | Command | Tests | Highlights |
|---|---|---|---|
| backend | `./mvnw test` (Docker needed) | 97 | route-driven user isolation, upload validation, storage path traversal |
| ai-service | `pytest` | 62 | PDFs generated in memory, real Tesseract on rendered text, schema safety scan |
| frontend | `npm test` | 46 | upload queue concurrency, XHR token refresh, viewer states |

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

---

## 10. Known limitations (accepted for now)

- **Documents stay `UPLOADED`.** Nothing processes them yet; the worker and AI client come in Tasks 24–25.
- **OCR makes small character mistakes** on photos (e.g. "a" read as "4"). Mitigated by showing the original next to extracted data and letting users edit (Tasks 34–35).
- **English OCR only** (`OCR_LANGUAGES=eng`). Other Tesseract languages can be added via config and packages.
- **The AI service Docker image wasn't built in the cloud sandbox** (Debian mirror blocked there). It builds on a normal machine.
- **From checkpoint 1:** no refresh-token cleanup job; backend and frontend not in Docker Compose yet; no CORS config (the dev proxy makes it unnecessary).
