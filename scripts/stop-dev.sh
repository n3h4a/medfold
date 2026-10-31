#!/usr/bin/env bash
# Stops what scripts/start-dev.sh started (AI service, backend, frontend) and the database
# container. Data is kept.
#   scripts/stop-dev.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .dev/start-dev.pid ] && kill -0 "$(cat .dev/start-dev.pid)" 2>/dev/null; then
  echo "Stopping the AI service, backend and frontend"
  kill -TERM "$(cat .dev/start-dev.pid)"
  for _ in $(seq 1 30); do kill -0 "$(cat .dev/start-dev.pid 2>/dev/null)" 2>/dev/null || break; sleep 1; done
else
  echo "start-dev.sh isn't running"
fi
echo "Stopping PostgreSQL (data kept)"
docker compose stop db
