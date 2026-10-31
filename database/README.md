# Database

PostgreSQL 16. The schema is created and evolved by **Flyway** migrations in
[`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration); the
backend applies them on start and Hibernate only validates against them (`ddl-auto: validate`).
[`schema.sql`](schema.sql) is a generated snapshot for reading, not something to run.

## Entity-relationship diagram

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "has sessions"
    users ||--o{ documents : owns
    documents ||--o{ document_pages : "text per page"
    documents ||--o{ lab_results : "test rows"
    documents ||--o{ medications : "medicines"
    documents ||--o{ document_terms : "explained terms"
    documents ||--o{ processing_jobs : "queued work"
    documents ||--o{ extraction_runs : "AI audit trail"

    users {
        uuid id PK
        varchar email UK "stored lower-case"
        varchar password_hash "BCrypt"
        varchar full_name
    }
    refresh_tokens {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK "SHA-256, never the token"
        timestamptz expires_at
        timestamptz revoked_at
    }
    documents {
        uuid id PK
        uuid user_id FK
        varchar storage_key UK
        varchar sha256 "unique per user"
        varchar status "UPLOADED, PROCESSING, READY, FAILED"
        varchar doc_type
        date doc_date
        varchar title
        text summary
        boolean user_edited
    }
    document_pages {
        uuid id PK
        uuid document_id FK
        int page_number
        text text
        varchar source "PDF_TEXT, TESSERACT, CLOUD, NONE"
        real confidence
    }
    lab_results {
        uuid id PK
        uuid document_id FK
        varchar test_name "as printed"
        varchar test_key "canonical, e.g. hba1c"
        varchar value_text "as printed"
        numeric value_numeric
        varchar ref_text "as printed"
        numeric ref_low
        numeric ref_high
        varchar flag "LOW, NORMAL, HIGH, computed by the backend"
        date observed_on
        boolean user_edited
    }
    medications {
        uuid id PK
        uuid document_id FK
        varchar name
        varchar extracted_name "as first read"
        varchar dosage
        varchar frequency
        date prescribed_on
        boolean user_edited
    }
    document_terms {
        uuid id PK
        uuid document_id FK
        varchar term
        varchar definition
    }
    processing_jobs {
        uuid id PK
        uuid document_id FK
        uuid user_id
        varchar status "PENDING, RUNNING, DONE, FAILED"
        int attempts
        timestamptz next_run_at
        timestamptz locked_at
    }
    extraction_runs {
        uuid id PK
        uuid document_id FK
        varchar llm_provider
        varchar llm_model
        varchar prompt_version
        boolean safety_filtered
        text raw_output
    }
    login_failures {
        bigint id PK
        char throttle_key "SHA-256"
        timestamptz failed_at
    }
```

Every table below `documents` also carries `user_id`, so each read can be scoped to the owner
without a join. `login_failures` stands alone: it is keyed by hashed email or client address.

## Relationships

- **Ownership.** `users` → `documents` → everything extracted from them. Deleting a user cascades
  through the whole tree in one transaction (`ON DELETE CASCADE` on every foreign key); the
  uploaded files are removed after that commits.
- **Reprocessing** replaces a document's pages, lab results, medicines and terms, except rows the
  user corrected (`user_edited`). Lab results are recognised by `test_key`, medicines by
  `extracted_name`.
- **Jobs.** A partial unique index allows at most one `PENDING` or `RUNNING` job per document.
- **Audit.** Every successful AI run leaves an `extraction_runs` row: OCR engine, model, prompt
  version, safety-filter outcome and the exact raw output.

## Indexes

| Index | Serves |
|---|---|
| `documents (user_id, doc_date DESC NULLS LAST, created_at DESC)` | The timeline, the app's main screen |
| `documents (user_id, sha256)` unique | Duplicate upload detection |
| `documents (user_id, status)`, `(user_id, doc_type)` | Filtering by state and type |
| `lab_results (user_id, test_key, observed_on)` | Trends: one test across all reports |
| `lab_results (document_id, position)`, same on `medications`, `document_terms` | A document's results in printed order |
| `medications (user_id, prescribed_on DESC)` | Medicines list |
| `processing_jobs (status, next_run_at)` | The worker's claim query |
| `processing_jobs (document_id) WHERE status IN ('PENDING','RUNNING')` unique | One active job per document |
| `refresh_tokens (token_hash)` unique, `(user_id)` | Token rotation, revoking all sessions |
| `login_failures (throttle_key, failed_at DESC)`, `(failed_at)` | Counting recent failures, deleting expired ones |
| `extraction_runs (document_id, created_at DESC)` | Latest run of a document |

Search uses `ILIKE` substring matching without an index; see
[ADR-003](../docs/decisions/ADR-003-search.md) for why that is the right trade-off at this scale.

## Migrations

| Version | Change |
|---|---|
| V1 `baseline` | Empty baseline |
| V2 `users_and_auth` | `users`, `refresh_tokens` |
| V3 `documents` | `documents` with status, extracted metadata, check constraints and the timeline index |
| V4 `extraction_results` | `document_pages`, `lab_results`, `medications`, `document_terms`, `processing_jobs`, `extraction_runs` |
| V5 `processing_jobs_user` | `processing_jobs.user_id`, so the worker reads documents through owner-scoped queries |
| V6 `login_failures` | Wrong-password attempts for throttling, shared by all backend instances |
| V7 `medications_extracted_name` | Name as first read, so reprocessing recognises renamed corrections |

**Rules for new migrations:** never edit an applied migration; add `V<n>__<what>.sql`. When adding
a `NOT NULL` column to a table that may have rows, add it nullable, backfill, then set
`NOT NULL` (see V5 and V7). Every new user-data table carries `user_id` and gets an owner-scoped
repository without `findById`/`findAll`.

## Demo data

Accounts and documents are created through the API rather than SQL, so passwords are hashed and
documents go through the real processing pipeline:

```bash
scripts/seed-demo-data.sh      # demo@medfold.local / medfold-demo-2026
scripts/reset-db.sh            # empty the local development database
```
