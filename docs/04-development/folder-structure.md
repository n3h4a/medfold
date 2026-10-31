# Folder structure

```
medfold/
├── README.md, LICENSE, CHANGELOG.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md,
│   SECURITY.md, ROADMAP.md, KNOWN_LIMITATIONS.md
├── docker-compose.yml          whole app on one machine (development, demo, CI)
├── .env.example                every setting, with safe local defaults
│
├── backend/                    Spring Boot API
│   ├── src/main/java/com/medfold/
│   │   ├── auth/               register, login, JWT, refresh tokens, login throttling
│   │   ├── user/               accounts
│   │   ├── document/           upload, timeline, files, corrections, reprocessing
│   │   ├── storage/            StorageService + local filesystem
│   │   ├── processing/         job queue, worker, result writer
│   │   ├── ai/                 AI service client
│   │   ├── records/            lab results, medicines, trends, range comparison
│   │   ├── search/             search with snippets
│   │   ├── account/            export, deletion
│   │   ├── health/             health endpoint
│   │   ├── common/             errors, paging
│   │   └── config/             security, scheduling, clock
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/       Flyway V1–V7
│   ├── src/test/java/          JUnit + Testcontainers, mirrors main
│   └── pom.xml, Dockerfile, README.md
│
├── frontend/                   React SPA
│   ├── src/
│   │   ├── api/                typed client, token refresh, endpoints
│   │   ├── auth/               session, login and register pages, route guards
│   │   ├── app/                router, layout, providers
│   │   ├── features/           documents, labs, medications, account, status
│   │   ├── components/         shared form controls
│   │   └── test/               test setup and API mocks
│   ├── public/                 favicon
│   ├── nginx.conf              production web server and /api proxy
│   └── package.json, Dockerfile, README.md
│
├── ai-service/                 FastAPI OCR and extraction
│   ├── app/
│   │   ├── ocr/                file types, PDF text layer, images, OCR providers
│   │   ├── extraction/         schema, prompt (prompts/), lab keys, safety check
│   │   ├── llm/                Anthropic and offline providers
│   │   └── routers/            /health, /v1/ocr, /v1/extract
│   ├── tests/                  pytest
│   └── pyproject.toml, requirements.txt (lock), Dockerfile, README.md
│
├── database/                   ER diagram, schema snapshot, indexes, migration notes
├── docs/                       00-project … 09-appendix (see docs/README.md)
├── docker/                     production overlay, Caddy, Docker notes
├── scripts/                    start-dev, stop-dev, seed-demo-data, reset-db, cleanup,
│                               backup, restore
├── tests/                      sample PDFs and images, their generator, k6 load test
├── postman/                    API collection and environments
├── demo/                       demo GIF and video, screenshots
├── assets/                     logo, diagrams (sources + SVG), charts, branding
└── .github/                    CI workflow, issue forms, pull request template
```

## Why this layout

- **Each service is self-contained:** its own build, Dockerfile, tests and README, so it can be
  built, tested and deployed alone.
- **Backend packages by feature,** so everything about documents is in one place. Inside each:
  `*Controller`, `*Service`, `*Repository`, entities and `dto/`.
- **Tests next to the code** they test (each tool's convention); `tests/` at the root holds what
  spans services: sample documents and the load test.
- **One home per topic.** The database is documented in `database/`, the API in
  `docs/03-design/api-design.md`; other pages link instead of repeating.
