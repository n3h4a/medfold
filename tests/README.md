# Tests

Unit and integration tests live next to the code they test; this folder holds what spans the
whole system.

| Where | What | Run |
|---|---|---|
| `backend/src/test` | JUnit + Testcontainers (real PostgreSQL): API, privacy isolation suite, worker, AI client | `cd backend && ./mvnw test` |
| `ai-service/tests` | pytest: OCR on generated PDFs and images, extraction, safety check | `cd ai-service && pytest` |
| `frontend/src/**/*.test.tsx` | Vitest + Testing Library: screens and flows against a mocked API | `cd frontend && npm test` |
| `postman/` | End-to-end API scenario (26 requests, 36 assertions) | see [postman/README.md](../postman/README.md) |
| `tests/fixtures/` | Synthetic demo documents (below) | `scripts/seed-demo-data.sh` |

CI (`.github/workflows/ci.yml`) runs all three suites, then starts the whole Docker stack,
seeds the fixtures (including the photographed prescription, read by real Tesseract OCR) and
runs the Postman collection through nginx.

## Fixtures

Every fixture is invented and says so on the page. `generate_reports.py` rebuilds them:

```bash
python tests/fixtures/generate_reports.py   # needs reportlab and Pillow (AI service dev deps)
```

| File | Shows |
|---|---|
| `reports/metabolic-panel-*.pdf` (4 dates) | HbA1c, glucose and lipids over 18 months: trends and range comparison |
| `reports/complete-blood-count-2026-03-02.pdf` | Blood count with values below the printed range, `7,400` style numbers |
| `reports/prescription-2026-05-20.pdf` | Medicines with dose, frequency and duration |
| `reports/chest-x-ray-2025-08-11.pdf` | Imaging report (no lab table) |
| `images/prescription-photo-2026-07-09.png` | Slightly rotated photo of a prescription: the OCR path |
