#!/usr/bin/env bash
# Deletes the LOCAL DEVELOPMENT database and uploaded files, so the next start is empty.
# For a production deployment use scripts/backup.sh and scripts/restore.sh instead.
#   scripts/reset-db.sh          (asks first; FORCE=yes skips the question)
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "${FORCE:-}" != "yes" ]; then
  read -r -p "Delete the local development database and uploads? Type 'reset' to continue: " answer
  [ "$answer" = "reset" ] || { echo "Cancelled."; exit 1; }
fi

echo "Removing the database container and its volume"
docker compose rm --stop --force db >/dev/null
docker volume rm --force "$(docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')_medfold_pgdata" >/dev/null

echo "Removing uploaded files in data/uploads (the folder itself stays)"
find data/uploads -mindepth 1 -not -name .gitkeep -delete

echo "Starting a fresh database"
docker compose up -d --wait db
echo "Done. The backend recreates the schema (Flyway) when it next starts."
