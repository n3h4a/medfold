# Rollback plan

How to undo a bad release. The approach depends on whether the release changed the database.

## Before every release

1. Take a backup: `scripts/backup.sh /var/backups/medfold` (database and files).
2. Note the running version: `git rev-parse --short HEAD`.
3. Check whether the release adds Flyway migrations:
   `git diff --name-only <current>..<new> -- backend/src/main/resources/db/migration`.

## A. No new migrations: roll back the code

The database is unchanged, so the previous version runs against it as before.

```bash
git checkout <previous-version>
docker compose -f docker-compose.yml -f docker/docker-compose.prod.yml up -d --build
```

Check System status and upload a sample document. Expected downtime: the time to rebuild and
restart the containers.

## B. New migrations ran: restore the backup

Flyway migrations only move forward, and the previous code validates the schema on start
(`ddl-auto: validate`), so it will refuse to run against a newer schema. Restore the backup taken
before the release:

```bash
git checkout <previous-version>
docker compose -f docker-compose.yml -f docker/docker-compose.prod.yml up -d --build
COMPOSE="docker compose -f docker-compose.yml -f docker/docker-compose.prod.yml" \
  scripts/restore.sh /var/backups/medfold/medfold-<time>.dump /var/backups/medfold/medfold-<time>-uploads.tar.gz
```

**Data written after the backup is lost** (uploads and corrections made since). To keep the window
small, release at quiet times and back up immediately before.

## C. Forward fix

If the problem is small and the release added migrations, a quick fix release is often better than
restoring: no data is lost. Write the fix as a new commit (and a new migration if needed); never
edit a migration that has already run.

## After a rollback

- Record what happened in the [bug log](../05-testing/bug-log.md) with the root cause.
- Add a test that would have caught it before re-releasing.
