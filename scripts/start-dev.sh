#!/usr/bin/env bash
# Starts the whole app for local development: PostgreSQL in Docker, then the AI service,
# backend and frontend on this machine. Ctrl+C stops everything this script started.
#   scripts/start-dev.sh
# Logs: .dev/<service>.log. Needs Docker, JDK 21, Node 20+, Python 3.12+.
set -euo pipefail
# Job control gives each service its own process group, so stopping a service also stops the
# processes it started (Maven runs the backend in a child JVM).
set -m
cd "$(dirname "$0")/.."

[ -f .env ] || { cp .env.example .env; echo "Created .env from .env.example (local defaults)."; }
mkdir -p .dev
echo $$ > .dev/start-dev.pid   # lets scripts/stop-dev.sh stop this from another terminal
pids=()
stop() {
  echo; echo "Stopping..."
  for pid in "${pids[@]}"; do kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
  [ "$(cat .dev/start-dev.pid 2>/dev/null)" = "$$" ] && find .dev -maxdepth 1 -name start-dev.pid -delete
}
trap stop EXIT INT TERM

echo "Starting PostgreSQL"
docker compose up -d --wait db

if [ ! -d ai-service/.venv ]; then
  echo "Creating the AI service virtualenv (first run only)"
  python3 -m venv ai-service/.venv
  ai-service/.venv/bin/pip install -q -e "ai-service[dev]"
fi
echo "Starting the AI service on :8000"
(cd ai-service && exec .venv/bin/uvicorn app.main:app --port 8000) > .dev/ai-service.log 2>&1 &
pids+=($!)

echo "Starting the backend on :8080 (first run downloads Maven dependencies)"
(cd backend && exec ./mvnw -q spring-boot:run) > .dev/backend.log 2>&1 &
pids+=($!)

[ -d frontend/node_modules ] || (cd frontend && npm install --no-audit --no-fund)
echo "Starting the frontend on :5173"
(cd frontend && exec node_modules/.bin/vite --strictPort) > .dev/frontend.log 2>&1 &
pids+=($!)

echo "Waiting for the backend..."
for _ in $(seq 1 120); do
  curl -sf http://localhost:8080/api/v1/health >/dev/null && break
  sleep 2
done
curl -sf http://localhost:8080/api/v1/health >/dev/null || { echo "Backend didn't start; see .dev/backend.log"; exit 1; }

echo
echo "Medfold is running: http://localhost:5173"
echo "Load demo data:     scripts/seed-demo-data.sh"
echo "Logs:               .dev/*.log    Stop: Ctrl+C or scripts/stop-dev.sh"
wait
