#!/usr/bin/env bash
# Stops the local Docker containers and removes build output and caches. Keeps your data:
# the database volume, data/uploads and .env are left alone (see reset-db.sh for those).
#   scripts/cleanup.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Stopping containers (volumes kept)"
docker compose down --remove-orphans

echo "Removing build output and caches"
for dir in backend/target frontend/dist frontend/node_modules/.vite .dev; do
  [ -e "$dir" ] && find "$dir" -mindepth 0 -delete && echo "  $dir"
done
find ai-service tests -type d \( -name __pycache__ -o -name .pytest_cache \) -prune -exec find {} -delete \; 2>/dev/null || true
echo "Done."
