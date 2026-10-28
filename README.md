# Medfold

Upload once. Find everything. Understand your medical history.

## Prerequisites

- Docker (with Compose)
- JDK 21
- Python 3.12+ and Tesseract OCR (only to run the AI service outside Docker)

## Run locally

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
