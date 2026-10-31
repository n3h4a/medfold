#!/usr/bin/env bash
# Backs up the database and the uploaded files of a running Docker Compose deployment.
#   scripts/backup.sh [backup-dir]        (default: ./backups)
# Creates <dir>/medfold-<timestamp>.dump (pg_dump custom format) and
# <dir>/medfold-<timestamp>-uploads.tar.gz. Run it from the repository root, e.g. daily from cron.
set -euo pipefail

COMPOSE=${COMPOSE:-docker compose}
DIR=${1:-backups}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$DIR"
# Medical data: only the owner may read the backups.
umask 077

DB_FILE="$DIR/medfold-$STAMP.dump"
FILES_FILE="$DIR/medfold-$STAMP-uploads.tar.gz"

echo "Dumping database to $DB_FILE"
$COMPOSE exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$DB_FILE.part"
mv "$DB_FILE.part" "$DB_FILE"

echo "Archiving uploaded files to $FILES_FILE"
$COMPOSE exec -T backend tar czf - -C /data uploads > "$FILES_FILE.part"
mv "$FILES_FILE.part" "$FILES_FILE"

echo "Backup complete. Copy these files off this server, e.g. to encrypted offsite storage."
