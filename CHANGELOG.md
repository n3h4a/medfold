# Changelog

All notable changes to Medfold. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

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
- **Documentation:** architecture, API, database, deployment, ADRs, security, performance,
  known limitations, roadmap.

### Fixed
- The backend called the AI service over HTTP/2 upgrade, which uvicorn rejects for requests with
  a body; it now uses HTTP/1.1.
- A renamed, corrected medicine was added a second time on reprocessing.
- "1 pages" on single-page documents; "a imaging report" in offline summaries.

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

[1.0.0]: https://github.com/n3h4a/medfold/compare/v0.5.0...v1.0.0
[0.5.0]: https://github.com/n3h4a/medfold/compare/v0.1.0...v0.5.0
[0.1.0]: https://github.com/n3h4a/medfold/releases/tag/v0.1.0
