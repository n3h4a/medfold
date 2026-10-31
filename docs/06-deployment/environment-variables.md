# Environment variables

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
