<p align="center">
  <img src="assets/branding/banner.png" alt="Medfold: upload once, find everything, understand your medical history" width="100%">
</p>

<p align="center">
  <a href="https://github.com/n3h4a/medfold/actions/workflows/ci.yml"><img src="https://github.com/n3h4a/medfold/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="docs/05-testing/test-plan.md#coverage"><img src="https://img.shields.io/badge/coverage-93%25-brightgreen" alt="Line coverage 93%"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT license"></a>
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4-6db33f" alt="Spring Boot 4">
  <img src="https://img.shields.io/badge/React-19-61dafb" alt="React 19">
  <img src="https://img.shields.io/badge/Python-3.12-3776ab" alt="Python 3.12">
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791" alt="PostgreSQL 16">
</p>

<p align="center">
  <img src="demo/quick-demo.gif" alt="Demo: log in, upload a thyroid report that is read automatically, see its results, an HbA1c trend and search" width="820">
  <br><sub>Upload → read automatically → results compared with the printed range → trends → search
  (<a href="demo/demo-video.mp4">video</a>). All data shown is synthetic.</sub>
</p>

## The problem

Medical history arrives as paperwork: PDFs from labs, printed prescriptions, phone photos of
reports. Every lab prints results differently and nothing lines them up over time. When a doctor
asks "what was your HbA1c last year?", the answer is buried in a folder.

## The solution

**Medfold** reads those documents for you. Upload a PDF or a photo; it extracts the text (exactly
from digital PDFs, by OCR from scans), turns it into structured records with an LLM, and puts
everything on a timeline: each test tracked across reports, every medicine listed, all of it
searchable. It **describes** what your documents say and never interprets them or gives advice.

## Features

| | |
|---|---|
| **Upload** | PDF, JPG, PNG up to 10 MB; drag and drop several at once with progress; type checked from content; duplicates detected |
| **Automatic reading** | Exact text from digital PDFs; Tesseract OCR for scans and photos; extraction of type, date, doctor, facility, lab results, medicines and plain-language definitions |
| **Lab results** | Values and ranges exactly as printed, a neutral "below / within / above printed range" comparison, each test charted across reports with unit conversion |
| **Timeline and search** | Documents by the date of the record; search any word across details, page text, tests and medicines |
| **Corrections** | Fix anything misread; corrections survive reprocessing |
| **Your data** | Download everything as JSON; delete the account and every file |
| **Security** | Rotating refresh tokens in HttpOnly cookies, rate-limited login, strict per-user isolation, HTTPS, CSP |

## Architecture

![Architecture: browser, nginx, Spring Boot API, PostgreSQL, file storage, AI service, Claude API](assets/diagrams/architecture.svg)

Three services: a **React** SPA; a **Spring Boot** API that owns all data and runs a background
job queue in **PostgreSQL**; and an internal **FastAPI** service that does OCR and LLM extraction.
Design details: [architecture](docs/03-design/architecture.md) ·
[components](docs/03-design/component-design.md) · [sequence diagrams](docs/03-design/sequence-diagrams.md) ·
[database](database/README.md) · [decision records](docs/03-design/adr/README.md).

## Screenshots

| Timeline | Document with extracted results |
|---|---|
| ![Timeline grouped by month with search](demo/screenshots/timeline.png) | ![Original PDF next to extracted details, test results and terms](demo/screenshots/document-detail.png) |
| **Trend across reports** | **Search** |
| ![HbA1c over two years with the printed range shaded](demo/screenshots/lab-trend.png) | ![Search for a medicine with a snippet](demo/screenshots/search.png) |

<details>
<summary>More: test results, medicines, mobile</summary>

| Test results | Medicines | Mobile |
|---|---|---|
| ![Every test with its latest value](demo/screenshots/test-results.png) | ![Medicines as written](demo/screenshots/medicines.png) | ![Trend on a phone](demo/screenshots/mobile-trend.png) |

</details>

## How it works

1. **Upload.** The file's type is checked from its bytes, it's streamed to storage with a SHA-256
   hash, and the document and a processing job are saved in **one transaction**. The request
   returns in ~15 ms.
2. **Queue.** A worker claims due jobs with `FOR UPDATE SKIP LOCKED`, so several workers never
   collide. Failures are retried with backoff; crashed jobs are recovered.
3. **Read.** The AI service takes each page's text layer, or renders it and runs Tesseract.
4. **Extract.** An LLM (Claude, or an offline rule-based reader) fills a strict schema that has
   **no field for diagnosis or advice**. Output is validated and wording safety-checked; every run
   is audited.
5. **Compare.** The backend, not the model, compares each value with the range printed on the
   same report, and only when both parse with certainty.
6. **Show.** The browser follows progress and shows the original next to the extracted data.

![OCR and extraction pipeline](assets/diagrams/ai-pipeline.svg)

## Tech stack

| Layer | Technologies |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, React Router, Vitest, Testing Library |
| Backend | Java 21, Spring Boot 4, Spring Security (JWT resource server), Spring Data JPA, Flyway, JUnit 5, Testcontainers, JaCoCo |
| AI service | Python 3.12, FastAPI, Pydantic, pypdf, pypdfium2, Pillow, Tesseract OCR, Anthropic SDK, pytest |
| Data | PostgreSQL 16 |
| Infrastructure | Docker Compose, nginx, Caddy (HTTPS), GitHub Actions, Postman/Newman, k6 |

## Folder structure

```
medfold/
├── frontend/      React SPA                         ├── docs/       00-project … 09-appendix
├── backend/       Spring Boot API                   ├── scripts/    start-dev, seed-demo-data, backup, …
├── ai-service/    FastAPI OCR and extraction        ├── tests/      sample documents, load test
├── database/      ER diagram, schema, migrations    ├── postman/    API collection
├── docker/        production overlay, Caddy         ├── demo/       GIF, video, screenshots
└── .github/       CI, issue and PR templates        └── assets/     logo, diagrams, charts
```

Annotated: [folder structure](docs/04-development/folder-structure.md).

## Installation

**Everything in Docker** (needs Docker with Compose):

```bash
git clone https://github.com/n3h4a/medfold.git
cd medfold
cp .env.example .env
docker compose up -d --build
API_URL=http://localhost:3000/api/v1 scripts/seed-demo-data.sh
```

Open http://localhost:3000 and log in as `demo@medfold.local` / `medfold-demo-2026`.

**For development** with hot reload (JDK 21, Node 20+, Python 3.12+):

```bash
scripts/start-dev.sh          # http://localhost:5173 · stop with scripts/stop-dev.sh
scripts/seed-demo-data.sh
```

The offline reader is the default, so no API key is needed. For Claude, set
`LLM_PROVIDER=anthropic` and `LLM_API_KEY` in `.env`
([all settings](docs/06-deployment/environment-variables.md)).

## API

REST + JSON under `/api/v1`, JWT bearer tokens with a rotating refresh cookie.

| | |
|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/refresh` | Sessions |
| `POST /documents` · `GET /documents` · `GET /documents/{id}/results` | Upload, timeline, extracted results |
| `PATCH /documents/{id}`, `/lab-results/{id}`, `/medications/{id}` | Corrections |
| `GET /labs/{testKey}` · `GET /search?q=` · `GET /medications` | Trends, search, medicines |
| `GET /account/export` · `DELETE /account` | Your data |

All 25 endpoints with errors and examples: [API design](docs/03-design/api-design.md).
Runnable: [Postman collection](postman/README.md).

## Quality

| | |
|---|---|
| **Tests** | 222 backend (real PostgreSQL) · 95 frontend · 140 AI service · end to end on the Docker stack in CI |
| **Coverage** | 92.6–93.5% of lines per service ([test plan](docs/05-testing/test-plan.md)) |
| **Privacy** | A test reads the route table and attacks every endpoint as another user |
| **Performance** | p95 6.5–11 ms for reads at 20 concurrent users ([load test](docs/05-testing/performance-testing.md)) |

<p>
  <img src="assets/charts/coverage.svg" alt="Line and branch coverage per service" width="49%">
  <img src="assets/charts/latency.svg" alt="API latency under load" width="49%">
</p>

## Deployment

`docker/docker-compose.prod.yml` adds Caddy for automatic HTTPS, closes every internal port and
requires real secrets; `scripts/backup.sh` and `scripts/restore.sh` handle backups.
Guide: [production deployment](docs/06-deployment/deployment-guide.md) ·
[checklist](docs/06-deployment/production-checklist.md) · [rollback plan](docs/06-deployment/rollback-plan.md).

## Roadmap

Next: password reset and two-factor authentication, typo-tolerant search, more OCR languages,
sharing a summary with a doctor, object storage. See [ROADMAP.md](ROADMAP.md),
[issues](https://github.com/n3h4a/medfold/issues) and [known limitations](KNOWN_LIMITATIONS.md).

## Contributing

Contributions are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[code of conduct](CODE_OF_CONDUCT.md). Security issues: [SECURITY.md](SECURITY.md). All
documentation: [docs/](docs/README.md).

## License

[MIT](LICENSE)

## Author

Built by [n3h4a](https://github.com/n3h4a).

> Medfold organises records; it is not a medical device and does not give medical advice.
> Every document in this repository and its screenshots is synthetic.
