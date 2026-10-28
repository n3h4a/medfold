# Medfold

Upload once. Find everything. Understand your medical history.

## Prerequisites

- Docker (with Compose)
- JDK 21

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

## Tests

```bash
cd backend
./mvnw test
```

Backend tests start a throwaway PostgreSQL container, so Docker must be running.
