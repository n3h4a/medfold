#!/usr/bin/env bash
# Restores a backup made by scripts/backup.sh into a running Docker Compose deployment.
#   scripts/restore.sh backups/medfold-<timestamp>.dump backups/medfold-<timestamp>-uploads.tar.gz
# The database is REPLACED by the backup. Uploaded files from the backup are written back; files
# that aren't in the backup are left in place (the restored database doesn't refer to them).
# The backend is stopped while restoring.
set -euo pipefail

COMPOSE=${COMPOSE:-docker compose}
DB_FILE=${1:?Usage: scripts/restore.sh <database .dump> <uploads .tar.gz>}
FILES_FILE=${2:?Usage: scripts/restore.sh <database .dump> <uploads .tar.gz>}
[ -f "$DB_FILE" ] || { echo "No such file: $DB_FILE" >&2; exit 1; }
[ -f "$FILES_FILE" ] || { echo "No such file: $FILES_FILE" >&2; exit 1; }

if [ "${FORCE:-}" != "yes" ]; then
  read -r -p "This replaces the current Medfold database. Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }
fi

echo "Stopping the backend so nothing writes while restoring"
$COMPOSE stop backend

echo "Restoring database from $DB_FILE"
$COMPOSE exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --exit-on-error' < "$DB_FILE"

echo "Restoring uploaded files from $FILES_FILE"
# The backend container is stopped, so unpack through a throwaway one that mounts the same volume.
$COMPOSE run --rm --no-deps -T --entrypoint tar backend xzf - -C /data < "$FILES_FILE"

echo "Starting the backend"
$COMPOSE start backend
echo "Restore complete."
