# ADR-009: Why PostgreSQL, and only PostgreSQL

**Status:** Accepted · **Related:** [ADR-002](ADR-002-storage.md) (where files live),
[ADR-003](ADR-003-search.md) (search), [ADR-006](ADR-006-job-queue.md) (job queue)

## Context

A typical stack for this app would use four stores: a relational database for records, Redis for
rate limiting, a message broker for background jobs, and Elasticsearch for search. For a
single-person health record with a few hundred documents per user, each extra store is another
thing to run, secure, back up and keep consistent, and consistency is the hard part: a document
whose job was queued in Redis but whose row was rolled back in the database is a bug that only
appears under failure.

## Decision

**One PostgreSQL 16 database holds everything that isn't a file**, and each job usually given to
another system is done with a PostgreSQL feature:

| Job | Usually | Here | Why it is enough |
|---|---|---|---|
| Records and ownership | — | Tables with `user_id` on every row, foreign keys with `ON DELETE CASCADE` | Deleting an account is one transaction |
| Background jobs | RabbitMQ / SQS | `processing_jobs` + `FOR UPDATE SKIP LOCKED` | The document and its job are saved in the same transaction; no lost or orphaned jobs |
| One active job per document | Application locks | Partial unique index `WHERE status IN ('PENDING','RUNNING')` | The database refuses a duplicate even under a race |
| Search | Elasticsearch | `ILIKE` plus `pg_trgm` similarity over titles, page text, test and medicine names | Milliseconds at one person's scale; finds "metfor" and "haemoglobn" ([ADR-011](ADR-011-typo-tolerant-search.md)) |
| Login throttling | Redis | `login_failures` rows, hashed keys, swept every 5 minutes | Shared by every backend instance; survives restarts |
| Data rules | Application code | `CHECK` constraints on statuses and types | Bad data can't be written even by a buggy caller |

## Alternatives considered

| Option | Why not |
|---|---|
| **MySQL** | Has `SKIP LOCKED` since 8.0, but no partial indexes (the one-active-job rule), and weaker `CHECK`/`DISTINCT ON` support used by search snippets. |
| **MongoDB** | Extracted data looks like documents, but trends, ownership and cascades are relational, and multi-document transactions are an add-on rather than the default. |
| **SQLite** | Perfect for one machine, but no `SKIP LOCKED` and a single writer; the backend is meant to run as several instances. |
| **PostgreSQL + Redis + a broker + Elasticsearch** | The textbook answer at scale, and the migration path if needed. Today it would multiply operational work and introduce cross-store consistency bugs. |

## Consequences

- **Good:** one thing to back up (`scripts/backup.sh`), one connection string, and every
  multi-step change is atomic. The load test (20 concurrent users) puts reads at a p95 of
  6–11 ms with this design ([performance-testing.md](../../05-testing/performance-testing.md)).
- **Cost:** search has no stemming or synonyms ("sugar" won't find "glucose"). Similar spellings
  and ranking came later with `pg_trgm` inside the same database ([ADR-011](ADR-011-typo-tolerant-search.md));
  a search engine is only worth it at a scale far beyond one person's records.
- **Cost:** the queue is polled every 2 seconds, so a job can wait up to 2 s before starting.
  `LISTEN/NOTIFY` would remove that if it ever matters.
