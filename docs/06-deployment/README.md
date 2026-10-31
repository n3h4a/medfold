# Deployment

Three ways to run Medfold, from quickest to production:

| | Command | Open | Use it for |
|---|---|---|---|
| **Docker, everything** | `docker compose up -d --build` | http://localhost:3000 | Trying it out, demos |
| **Local development** | `scripts/start-dev.sh` | http://localhost:5173 | Working on the code (hot reload) |
| **Production** | see [deployment-guide.md](deployment-guide.md) | `https://<your domain>` | A real server with HTTPS and backups |

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

Every setting, its default and who uses it: [environment-variables.md](environment-variables.md).

## Production

[deployment-guide.md](deployment-guide.md), then the [production checklist](production-checklist.md) and the
[rollback plan](rollback-plan.md): a Linux server with Docker, HTTPS by Caddy and Let's Encrypt,
only ports 80/443 open, required secrets enforced, daily backups and tested restore.
