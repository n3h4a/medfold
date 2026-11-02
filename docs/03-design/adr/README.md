# Architecture Decision Records

Short records of decisions that shaped Medfold, written so that someone joining the project can see
why it is built this way, not only how: the context, what was decided, what else was
considered, and what it costs. Format: [Michael Nygard's ADR](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001-authentication.md) | Short-lived JWT access tokens + rotating refresh token in an HttpOnly cookie | Accepted |
| [ADR-002](ADR-002-storage.md) | PostgreSQL for data; files behind a `StorageService` interface, local disk first | Accepted |
| [ADR-003](ADR-003-search.md) | Substring search in PostgreSQL, no search engine | Amended by ADR-011 |
| [ADR-004](ADR-004-ocr.md) | PDF text layer first, Tesseract OCR only for pages without text | Accepted |
| [ADR-005](ADR-005-service-split.md) | Spring Boot for the product, a separate Python FastAPI service for AI | Accepted |
| [ADR-006](ADR-006-job-queue.md) | Background processing through a PostgreSQL job table (`SKIP LOCKED`), not a message broker | Accepted |
| [ADR-007](ADR-007-ai-safety-boundary.md) | The AI only describes and copies; anything evaluative is computed deterministically | Accepted |
| [ADR-008](ADR-008-why-spring-boot.md) | **Why Spring Boot?** Java 21 + Spring for the public API, where mistakes are most costly | Accepted |
| [ADR-009](ADR-009-why-postgresql.md) | **Why PostgreSQL?** One database also does the queue, search and throttling | Accepted |
| [ADR-010](ADR-010-ocr-before-ai.md) | **Why OCR before AI?** The model reads extracted text, not page images | Accepted |
| [ADR-011](ADR-011-typo-tolerant-search.md) | Typo-tolerant, ranked search with `pg_trgm`, exact matches first | Accepted |
