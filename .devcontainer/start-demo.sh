#!/usr/bin/env bash
# Starts the full stack with the offline AI reader (no API key needed) and loads the demo account.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || cp .env.example .env
export VITE_DEMO_EMAIL=demo@medfold.local VITE_DEMO_PASSWORD=medfold-demo-2026
docker compose up -d --build
echo "Waiting for the API (the first build takes a few minutes)..."
for _ in $(seq 1 120); do
  curl -sf http://localhost:3000/api/v1/health >/dev/null && break
  sleep 5
done
API_URL=http://localhost:3000/api/v1 scripts/seed-demo-data.sh
echo "Medfold is running on port 3000. Log in with the demo account button."
