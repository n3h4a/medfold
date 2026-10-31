# Test plan

## Strategy

Most confidence comes from tests that use real components: a real PostgreSQL (Testcontainers),
real HTTP servers, real PDFs and images, and an end-to-end run of the built Docker images. Mocks
are used at service boundaries only (the AI service in backend tests, the backend in frontend
tests, the language model in AI service tests).

| Level | What | Tools | Where |
|---|---|---|---|
| Unit | Range parsing, unit conversion, entity state machines, safety wording, lab keys | JUnit, Vitest, pytest | each service |
| Integration | API endpoints against PostgreSQL, job queue concurrency, upload → worker → AI stand-in → database, AI client against a real HTTP server | Spring Boot Test, Testcontainers, MockMvc | `backend/src/test` |
| Component | Screens and flows through the real router, against a mocked API | Vitest, Testing Library | `frontend/src` |
| Security | Every route classified; every owned route attacked as another user; login throttling | `UserIsolationTest`, `LoginThrottle*Test` | backend |
| End to end | Built Docker images; 8 sample documents processed, including OCR of a photo; 26 API requests | CI, `seed-demo-data.sh`, Newman | `.github/workflows/ci.yml` |
| Performance | Latency under load, upload-to-ready time | k6 | [performance testing](performance-testing.md) |
| Manual | Release checklist below | browser | — |

## Coverage

![Line and branch coverage per service](../../assets/charts/coverage.svg)

| Service | Tests | Lines | Branches | Report |
|---|---|---|---|---|
| Backend | 222 | 93.3% | 82.5% | `backend/target/site/jacoco/index.html` |
| Frontend | 95 | 93.5% | 82.3% | `frontend/coverage/index.html` |
| AI service | 140 (7 need Tesseract) | 92.6% | 89.4% | `ai-service/htmlcov/index.html` |

Measured 2026-10-31. CI publishes the three HTML reports as artifacts on every run.

```bash
(cd backend && ./mvnw test)                    # Docker needed (Testcontainers)
(cd frontend && npm run test:coverage)
(cd ai-service && pytest --cov --cov-report=html)
```

## What the important suites prove

| Suite | Proves |
|---|---|
| `UserIsolationTest` | No route can read or change another user's data; new routes can't be added unclassified |
| `JobProcessingTest` | Two concurrent workers never claim the same job; retries, crash recovery, permanent failures |
| `DocumentPipelineTest` | Upload → worker → AI service (a stand-in on a real port) → stored results, failures, reprocessing with corrections |
| `AiServiceClientTest` | Wire format (multipart, JSON, HTTP/1.1), every error mapped to retry or permanent, timeouts |
| `LabFlagCalculatorTest` | 38 printed-range formats, including the ones that must get no comparison |
| AI `test_safety.py` | 24 unsafe and 8 safe phrasings; a prompt-injection document can't get advice through |
| AI `test_extraction_schemas.py` | The schema has no diagnosis or advice field and rejects unknown fields |
| Frontend `auth.test.tsx` | Session restore, single shared refresh for parallel requests, throttling message |

## CI

Every push to `main` and every pull request runs: backend tests, AI service tests with Tesseract,
frontend lint, tests and build, deployment-config validation, then the end-to-end job on the
Docker stack. Any failing job marks the commit or pull request as failed.

## Manual release checklist

Before tagging a release, on the Docker stack with demo data (`docker compose up -d --build`,
`API_URL=http://localhost:3000/api/v1 scripts/seed-demo-data.sh`):

- [ ] Register a new account, log out, log in again; reload keeps the session.
- [ ] Upload a PDF and the sample photo together; both reach Ready; progress shows per file.
- [ ] Upload the same file again: duplicate message with a link to the existing document.
- [ ] Open a document: the original shows next to the results; a value below range is labelled.
- [ ] Edit a lab value, process the document again: the correction is kept, no duplicate row.
- [ ] Trend page: chart and table; hover and keyboard focus show values.
- [ ] Search for a medicine and for a word only in the page text.
- [ ] Phone width (390 px): every page usable, no horizontal scrolling except tables.
- [ ] Download my data: the JSON opens and contains the documents.
- [ ] Delete the account with a wrong password (refused), then the right one (back to login).
- [ ] System status shows the AI service as available.
