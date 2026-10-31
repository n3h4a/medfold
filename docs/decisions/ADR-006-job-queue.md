# ADR-006: Background processing through a PostgreSQL job table

**Status:** Accepted

## Context

Reading a document takes from a fraction of a second (digital PDF, offline reader) to a minute
(multi-page scan plus an LLM call with a retry). The upload request must return immediately, work
must survive restarts, temporary AI outages must be retried, and two workers must never process
the same document.

## Decision

- An upload inserts the document **and** its `processing_jobs` row in one transaction.
- Workers claim due jobs with one statement:
  `UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED LIMIT n) RETURNING id`, so concurrent
  workers (threads or instances) never get the same job and never wait on each other.
- Processing runs **between** two short transactions: no transaction or row lock is held during
  OCR or LLM calls.
- Failures are classified: document problems fail at once with a plain message; outages retry
  after 30 s, 60 s, 120 s (capped at an hour, 3 attempts). Jobs left `RUNNING` by a crashed worker
  are recovered after 10 minutes.
- A partial unique index allows one waiting or running job per document.

## Alternatives considered

| Option | Why not |
|---|---|
| RabbitMQ / Kafka / SQS | Another system to run and monitor; enqueueing would no longer share the upload's transaction (dual-write problem) |
| `@Async` in-memory tasks | Lost on restart; no retries or visibility |
| Processing inside the upload request | Slow requests, timeouts, and a lost upload if the AI service is down |

## Consequences

- Exactly-once enqueueing for free, durable retries, and job state you can query with SQL.
- Polling every 2 seconds adds a little latency and database load; fine at this scale, and the
  claim query is index-backed.
- Throughput is one worker per backend instance processing jobs sequentially; more instances
  scale it horizontally without changes.
