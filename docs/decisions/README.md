# Architecture Decision Records

Short records of decisions that shaped Medfold: the context, what was decided, what else was
considered, and what it costs. Format: [Michael Nygard's ADR](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001-authentication.md) | Short-lived JWT access tokens + rotating refresh token in an HttpOnly cookie | Accepted |
| [ADR-002](ADR-002-storage.md) | PostgreSQL for data; files behind a `StorageService` interface, local disk first | Accepted |
| [ADR-003](ADR-003-search.md) | Substring search in PostgreSQL, no search engine | Accepted |
| [ADR-004](ADR-004-ocr.md) | PDF text layer first, Tesseract OCR only for pages without text | Accepted |
| [ADR-005](ADR-005-service-split.md) | Spring Boot for the product, a separate Python FastAPI service for AI | Accepted |
| [ADR-006](ADR-006-job-queue.md) | Background processing through a PostgreSQL job table (`SKIP LOCKED`), not a message broker | Accepted |
| [ADR-007](ADR-007-ai-safety-boundary.md) | The AI only describes and copies; anything evaluative is computed deterministically | Accepted |
