# Deployment

Three ways to run Medfold, from quickest to production:

| | Command | Open | Use it for |
|---|---|---|---|
| **Docker, everything** | `docker compose up -d --build` | http://localhost:3000 | Trying it out, demos |
| **Local development** | `scripts/start-dev.sh` | http://localhost:5173 | Working on the code (hot reload) |
| **Production** | see [production.md](production.md) | `https://<your domain>` | A real server with HTTPS and backups |

Then load the synthetic demo data with `scripts/seed-demo-data.sh` and log in as
`demo@medfold.local` / `medfold-demo-2026`.

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Docker with Compose | Compose ≥ 2.24 | Everything (PostgreSQL always runs in Docker) |
| JDK | 21 | Backend outside Docker |
| Node.js | 20+ | Frontend outside Docker |
| Python | 3.12+ | AI service outside Docker |
| Tesseract | 5 (`eng`, `osd`) | OCR of scans and photos outside Docker; digital PDFs work without it |

## Docker, everything

```bash
cp .env.example .env
docker compose up -d --build
scripts/seed-demo-data.sh       # optional; set API_URL=http://localhost:3000/api/v1
```

| Service | Image | Port (host) |
|---|---|---|
| `frontend` | nginx serving the built SPA, proxying `/api` | 3000 |
| `backend` | Spring Boot on a JRE, non-root | 8080 |
| `ai-service` | FastAPI with Tesseract, non-root | 8000 |
| `db` | `postgres:16-alpine` | 5432 |

Data lives in the `medfold_pgdata` and `medfold_uploads` volumes. `docker compose down` keeps them;
`docker compose down -v` deletes them. More in [docker/README.md](../../docker/README.md).

## Local development

```bash
scripts/start-dev.sh
```

Starts PostgreSQL in Docker, then the AI service (creating its virtualenv on first run), the
backend and the Vite dev server, with logs in `.dev/`. Ctrl+C stops everything. To run a service
on its own instead:

```bash
docker compose up -d db
(cd backend && ./mvnw spring-boot:run)                                  # :8080
(cd ai-service && .venv/bin/uvicorn app.main:app --reload --port 8000)  # :8000
(cd frontend && npm run dev)                                            # :5173, proxies /api
```

`scripts/reset-db.sh` empties the development database and uploads; `scripts/cleanup.sh` stops
containers and removes build output.

## Environment variables

All configuration comes from environment variables. Copy `.env.example` to `.env`; the backend
and the AI service also read the repository-root `.env` when started from their own folders.
Real environment variables always win.

| Variable | Default | Used by | Meaning |
|---|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `medfold`, `medfold`, `change_me` | db, backend | Database credentials |
| `POSTGRES_HOST`, `POSTGRES_PORT` | `localhost`, `5432` | backend | Where the database is (`db` inside Compose) |
| `JWT_SECRET` | none, **required** | backend | Signs access tokens; at least 32 characters (`openssl rand -hex 32`) |
| `JWT_ACCESS_TOKEN_TTL` | `PT15M` | backend | Access-token lifetime (ISO-8601 duration) |
| `REFRESH_TOKEN_TTL` | `P7D` | backend | Refresh-token lifetime |
| `REFRESH_COOKIE_SECURE` | `true` | backend | Send the refresh cookie over HTTPS only. `false` only for plain-HTTP local development |
| `MEDFOLD_PROCESSING_ENABLED` | `true` | backend | Run the background worker in this instance |
| `STORAGE_ROOT` | `../data/uploads` | backend | Folder for uploaded files (`/data/uploads` in Docker) |
| `AI_SERVICE_URL` | `http://localhost:8000` | backend | Where the backend reaches the AI service |
| `AI_INTERNAL_KEY` | none, **required** | backend, AI service | Shared secret; the AI service rejects every call without it |
| `OCR_PROVIDER` | `tesseract` | AI service | OCR engine |
| `OCR_LANGUAGES` | `eng` | AI service | Tesseract languages, e.g. `eng+hin` (traineddata must be installed) |
| `LLM_PROVIDER` | `mock` | AI service | `mock`: offline rule-based reader, no key; `anthropic`: Claude |
| `LLM_MODEL` | `claude-opus-5-5` | AI service | Model for `anthropic` |
| `LLM_API_KEY` | empty | AI service | Anthropic key; empty uses `ANTHROPIC_API_KEY` from the environment |
| `FRONTEND_PORT`, `BACKEND_PORT`, `AI_SERVICE_PORT` | `3000`, `8080`, `8000` | Compose | Host ports |
| `MEDFOLD_DOMAIN` | empty | Caddy (production) | Public host name for the HTTPS certificate |

Finer settings (worker batch size, retry delays, login-throttling limits, upload limits) are in
[`backend/src/main/resources/application.yml`](../../backend/src/main/resources/application.yml)
and `ai-service/app/config.py`, each overridable through the environment.

## Production

[production.md](production.md): a Linux server with Docker, HTTPS by Caddy and Let's Encrypt,
only ports 80/443 open, required secrets enforced, daily backups and tested restore.
