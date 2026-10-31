# Tests

Unit and integration tests live next to the code they test, as each tool expects; this folder holds
what spans the whole system. The strategy and coverage are in the
[test plan](../docs/05-testing/test-plan.md).

| Where | What | Run |
|---|---|---|
| `backend/src/test` | JUnit + Testcontainers (real PostgreSQL): API, privacy isolation suite, worker, AI client | `cd backend && ./mvnw test` |
| `ai-service/tests` | pytest: OCR on generated PDFs and images, extraction, safety check | `cd ai-service && pytest` |
| `frontend/src/**/*.test.tsx` | Vitest + Testing Library: screens and flows against a mocked API | `cd frontend && npm test` |
| `postman/` | End-to-end API scenario (26 requests, 36 assertions) | see [postman/README.md](../postman/README.md) |
| `tests/sample-pdfs`, `tests/sample-images` | Synthetic demo documents (below) | `scripts/seed-demo-data.sh` |
| `tests/performance` | k6 load test and its latest results | see [performance testing](../docs/05-testing/performance-testing.md) |

CI (`.github/workflows/ci.yml`) runs all three suites, then starts the whole Docker stack,
seeds the sample documents (including the photographed prescription, read by real Tesseract OCR) and
runs the Postman collection through nginx.

## Sample documents

Every sample is invented and says so on the page. `test-data/generate_samples.py` rebuilds them,
byte-identical on every run:

```bash
python tests/test-data/generate_samples.py   # needs reportlab and Pillow (AI service dev deps)
```

| File | Shows |
|---|---|
| `sample-pdfs/metabolic-panel-*.pdf` (4 dates) | HbA1c, glucose and lipids over 18 months: trends and range comparison |
| `sample-pdfs/complete-blood-count-2026-03-02.pdf` | Blood count with values below the printed range, `7,400` style numbers |
| `sample-pdfs/prescription-2026-05-20.pdf` | Medicines with dose, frequency and duration |
| `sample-pdfs/chest-x-ray-2025-08-11.pdf` | Imaging report (no lab table) |
| `sample-images/prescription-photo-2026-07-09.png` | Slightly rotated photo of a prescription: the OCR path |
