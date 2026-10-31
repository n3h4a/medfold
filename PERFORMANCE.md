# Performance

Medfold is built for one person's records: tens to a few hundred documents. The design keeps
requests fast and puts slow work (OCR, LLM calls) in the background.

## Measured

Local development stack (`scripts/start-dev.sh`) on a laptop-class container, PostgreSQL in
Docker, offline reader (`LLM_PROVIDER=mock`), digital PDFs, demo account with 8 documents. Median
of 11 requests.

| Operation | Time |
|---|---|
| Upload request (2 KB PDF: type check, hash, store, enqueue) | ~30 ms |
| Upload → `READY` (queue wait + OCR + extraction + save) | ~2 s, mostly the worker's 2 s polling interval |
| Timeline (`GET /documents`) | ~5 ms |
| Search, two words across all fields and page text | ~8 ms |
| One test's trend (`GET /labs/{testKey}`) | ~5 ms |
| A document's results | ~7 ms |

With real inputs, processing time is dominated by the AI service, not the API:

| Step | Typical time |
|---|---|
| PDF text layer, per page | ~30 ms |
| Tesseract OCR of a scanned page or photo | 1–2 s per page |
| Claude extraction (one call; a corrective retry doubles it) | estimated 5–30 s depending on length and model (not measured here) |

## Design choices that keep it fast

- **Asynchronous processing.** Uploads return as soon as the file is stored and the job queued
  ([ADR-006](docs/decisions/ADR-006-job-queue.md)). The browser polls a tiny status endpoint
  every 3 s, and only while something is processing.
- **No transaction held during AI calls.** The worker commits before calling the AI service and
  writes results in a second short transaction, so slow models never block the database.
- **Text layer before OCR.** Digital PDFs skip OCR entirely ([ADR-004](docs/decisions/ADR-004-ocr.md)).
- **Indexes for every hot path** ([database/README.md](database/README.md#indexes)): the timeline
  `(user_id, doc_date DESC NULLS LAST, created_at DESC)`, trends `(user_id, test_key, observed_on)`,
  the worker's claim `(status, next_run_at)`, per-document result order.
- **Streaming uploads.** Files are streamed to disk while the SHA-256 is computed, never held in
  memory; downloads are streamed with range support.
- **Bounded work.** 10 MB per file, 50 PDF pages, 20 OCR pages, a per-page OCR timeout,
  150,000 characters per extraction, page size ≤ 100 for lists, 50 search results.
- **Cheap health checks.** The AI service's availability is cached for 10 s, so status polling
  never floods it.
- **Frontend.** One production bundle with hashed assets cached for a year by nginx; server state
  cached by TanStack Query; images and PDFs shown from `blob:` URLs that are released on leave.

## Large PDFs

A 50-page scanned PDF takes about a minute of OCR. It runs in the background, the user sees
"Processing", and if the AI service is restarted mid-way the job is retried. Pages beyond the OCR
limit are recorded as pages without text rather than failing the document.

## Scaling further

| Bottleneck | Next step |
|---|---|
| One worker per backend processes jobs sequentially | Run more backend instances: the queue is safe for concurrent workers (`SKIP LOCKED`) and login throttling is shared through the database |
| OCR CPU | Run more AI service replicas behind the backend; the service is stateless |
| Search without an index | Add `pg_trgm` GIN indexes ([ADR-003](docs/decisions/ADR-003-search.md)) |
| Local-disk file storage | An object-storage implementation of `StorageService` ([ADR-002](docs/decisions/ADR-002-storage.md)) |
| Polling latency | Shorter poll interval, or `LISTEN/NOTIFY` to wake the worker on upload |
