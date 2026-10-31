# ADR-002: PostgreSQL for data, files behind a storage interface

**Status:** Accepted

## Context

Medfold stores two kinds of data: structured records (users, documents, lab results, medicines,
jobs) and the uploaded files themselves (PDFs and images up to 10 MB). Records must be strictly
owner-scoped; files must never be reachable by guessing a path.

## Decision

- **PostgreSQL 16** for everything structured, with **Flyway** migrations and Hibernate in
  validate-only mode. One database also carries the job queue ([ADR-006](ADR-006-job-queue.md)),
  search ([ADR-003](ADR-003-search.md)) and login throttling.
- **Repositories are owner-scoped by construction:** user-data repositories extend Spring Data's
  plain `Repository` and declare only `findByIdAndUserId`-style methods, so an unscoped
  `findById` doesn't exist to be misused.
- **Files** go through a `StorageService` interface. The current implementation writes to a local
  folder (a Docker volume in production) under server-generated keys `{userId}/{uuid}.{ext}`,
  streams uploads to a temporary file, computes SHA-256 on the way and moves atomically.
- Files are deleted only after the database delete commits, so a failure leaves an orphan file,
  never a record pointing at nothing.

## Alternatives considered

| Option | Why not (yet) |
|---|---|
| Files as `bytea` in PostgreSQL | Bloats the database and its backups; streaming is awkward |
| S3 or another object store from day one | Adds a cloud dependency to local development; the interface makes it a drop-in change later |
| A document database (MongoDB) for extracted data | Lab results, trends and ownership are relational; constraints and joins matter here |

## Consequences

- One database to run, back up and restore (`scripts/backup.sh` dumps it next to the files).
- Moving to object storage means one new `StorageService` implementation; callers never see paths.
- Local-disk storage ties uploads to one host until then (see
  [KNOWN_LIMITATIONS.md](../../KNOWN_LIMITATIONS.md)).
