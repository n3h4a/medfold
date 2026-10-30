# Medfold

Upload once. Find everything. Understand your medical history.

Architecture and current status: [docs/architecture.md](docs/architecture.md)

## Prerequisites

- Docker (with Compose)
- JDK 21
- Node.js 20+
- Python 3.12+ and Tesseract OCR (only to run the AI service outside Docker)

## Run everything with Docker

```bash
cp .env.example .env
docker compose up -d --build
```

Open http://localhost:3000. Set real values for `POSTGRES_PASSWORD`, `JWT_SECRET` and `AI_INTERNAL_KEY` in `.env` first.
nginx serves the app and forwards `/api` to the backend; uploads are kept in the `medfold_uploads` volume.

## Run locally for development

```bash
cp .env.example .env
docker compose up -d db
```

Start the backend (first run downloads Maven and dependencies):

```bash
cd backend
./mvnw spring-boot:run
```

Check it: `curl http://localhost:8080/api/v1/health`

### AI service

With Docker (Tesseract is included in the image):

```bash
docker compose up -d --build ai-service
```

Or locally:

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000
```

Check it: `curl http://localhost:8000/health` — API docs at http://localhost:8000/docs

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. API calls to `/api` are proxied to the backend on port 8080.

## Tests

```bash
cd backend
./mvnw test
```

Backend tests start a throwaway PostgreSQL container, so Docker must be running.

```bash
cd ai-service
pytest
```

```bash
cd frontend
npm test
```
