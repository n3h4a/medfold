# Changelog

All notable changes to Medfold. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **Typo-tolerant, ranked search** ([#3](https://github.com/n3h4a/medfold/issues/3),
  [ADR-011](docs/03-design/adr/ADR-011-typo-tolerant-search.md)): misspellings and OCR errors
  ("haemoglobn", "platlet") find the document, marked "Similar spelling" with the matched word
  highlighted; exact matches rank first. Migration V8 adds `pg_trgm` and GIN trigram indexes;
  search results gain `highlights` and `exact`.

## [1.1.0] - 2026-10-31 - Polish and proof

### Added
- **Overview** home page: counts, latest results outside the printed range with sparklines,
  recent documents, the latest prescription, and onboarding for new accounts. The document
  timeline moves to `/documents`.
- **Visit summary** (`/summary`): one printable page for an appointment with every test's latest
  value, printed range, comparison and previous value, the latest prescription and the sources.
- **Search palette** (Ctrl/⌘ K) for pages, tests and document text from anywhere.
- **Dark mode**, a sidebar layout, icons, a timeline rail, type filters, highlighted search
  matches, skeleton loading states and a redesigned login page.
- Lab summaries include the printed range and the previous value (`latestRefText`,
  `previousValueText`, `previousObservedOn`).
- `scripts/verify-claims.sh` re-measures every number the README quotes;
  `scripts/security-check.sh` attacks a running stack from outside (22 checks).
- Code audit report, ADR-008 (why Spring Boot), ADR-009 (why PostgreSQL), ADR-010 (why OCR before
  AI), `FEATURE_MATRIX.md`, `PROJECT_STATUS.md`.
- A recorded, captioned demo video with a narration script; a system overview image, a numbers
  card and an animated request flow, all generated from source.
- A GitHub Codespaces setup and settings for a public demo (`VITE_DEMO_EMAIL`, `VITE_DEMO_PASSWORD`).

### Fixed
- With several backend instances, a job running longer than `stale-after` could be finished by
  two workers at once, and the later one aborted its whole worker cycle. Jobs are now finished
  only by the worker that holds the current attempt; `stale-after` is 15 minutes.
- The AI service coverage was quoted as 92.6%, measured without Tesseract; it is 97.1%.

## [1.0.0] - 2026-10-31 - Demo ready

### Added
- **Search** by any word across document details, page text, test names and medicines, with snippets.
- **Corrections:** edit document details, lab results and medicines, or remove wrongly read rows;
  corrections survive reprocessing and re-date related results.
- **Account control:** download all data as JSON; delete the account and everything in it.
- **Trends across units:** unit spellings unified and standard conversions per test (glucose,
  cholesterol, creatinine, HbA1c % ↔ mmol/mol, …), labelled "printed as …".
- **Security:** login and account-deletion throttling shared across instances; strict CSP and
  security headers; production overlay with HTTPS (Caddy), required secrets, Secure cookies.
- **Operations:** System status shows AI service availability; refresh-token cleanup; Docker
  images for every service; `docker compose up` runs the whole app; backup and restore scripts.
- **Developer experience:** `scripts/start-dev.sh`, `seed-demo-data.sh`, `reset-db.sh`,
  `cleanup.sh`; synthetic demo reports; Postman collection; CI for all services plus an
  end-to-end job on the Docker stack.
- **Documentation** in numbered sections (project, research, product, design, development,
  testing, deployment, maintenance, appendix): architecture, components, sequence and state
  diagrams, API, database with ER diagram, security design, 7 ADRs, use cases with acceptance
  criteria traced to tests, production checklist, rollback plan, bug log, glossary, licenses.
- **Quality evidence:** coverage reports in every service (92.6–93.5% of lines) published by CI;
  a k6 load test (p95 under 12 ms for reads at 20 users); charts drawn from both.
- **Project files:** code of conduct, security policy, banner, rendered diagrams, `stop-dev.sh`,
  a pinned `requirements.txt` for the AI service image.

### Fixed
- The backend called the AI service over HTTP/2 upgrade, which uvicorn rejects for requests with
  a body; it now uses HTTP/1.1.
- A renamed, corrected medicine was added a second time on reprocessing.
- "1 pages" on single-page documents; "a imaging report" in offline summaries.
- Stopping the development scripts left Vite and the backend JVM running.

## [0.5.0] - 2026-10-30 - Core MVP

### Added
- **Documents end to end:** upload with content-based type detection and duplicate detection,
  owner-scoped storage, timeline, detail page with an authenticated file viewer.
- **Reading:** `/v1/ocr` (PDF text layer, Tesseract for scans and photos) and `/v1/extract`
  (Claude or an offline reader), validated against a schema with no room for diagnosis or advice,
  plus a safety check on free text.
- **Processing pipeline:** PostgreSQL job queue with `SKIP LOCKED`, retries with backoff, crash
  recovery; results stored with an audit trail of every AI run.
- **Records:** lab results compared with the printed reference range, medicines, explained terms;
  per-test trends; timeline grouped by record month.
- **Privacy:** route-driven isolation test suite that attacks every owned endpoint as another user.

## [0.1.0] - 2026-10-28 - Project setup

### Added
- Monorepo with PostgreSQL in Docker Compose, Spring Boot API with Flyway, FastAPI AI service
  with Tesseract, React + Vite + Tailwind frontend.
- Authentication: register and login with BCrypt, 15-minute JWT access tokens, rotating refresh
  tokens in an HttpOnly cookie with reuse detection, protected routes and automatic refresh.

[Unreleased]: https://github.com/n3h4a/medfold/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/n3h4a/medfold/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/n3h4a/medfold/compare/v0.5.0...v1.0.0
[0.5.0]: https://github.com/n3h4a/medfold/compare/v0.1.0...v0.5.0
[0.1.0]: https://github.com/n3h4a/medfold/releases/tag/v0.1.0
