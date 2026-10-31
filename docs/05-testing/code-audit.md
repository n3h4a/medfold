# Code audit

A repository can look better than the code inside it. This audit checks that the code lives up to
what the README claims: that the numbers are real, the security properties hold when attacked from
outside, and the riskiest code paths are correct. Performed 2026-10-31 on `main` before v1.1.0.

## 1. Are the numbers real?

Every figure quoted in the README was re-measured from the code with
[`scripts/verify-claims.sh`](../../scripts/verify-claims.sh), which runs all three test suites with
coverage and counts endpoints, tables and ADRs directly from the source:

| Claim | Measured | How |
|---|---|---|
| Tests | **471**: backend 223, frontend 108, AI service 140; 0 failing | Surefire XML, Vitest output, pytest JUnit XML |
| Line coverage | backend 93.3%, frontend 94.7%, AI service 97.1% | JaCoCo, V8, coverage.py |
| Branch coverage | backend 82.6%, frontend 82.5%, AI service 95.2% | same |
| REST endpoints | 25 backend + 4 AI service | `@…Mapping` / `@router.…` in source |
| Database tables | 10, created by 7 Flyway migrations | `CREATE TABLE` in migrations |
| Read latency | p95 6.5–11 ms at 20 concurrent users | k6, [performance-testing.md](performance-testing.md) |

**One number was wrong, in the unflattering direction:** the AI service coverage had been quoted
as 92.6%. That was measured on a machine without Tesseract, where 7 OCR tests are skipped. CI
installs Tesseract and covers 97.1%. Corrected everywhere.

## 2. Does the security hold from outside?

[`scripts/security-check.sh`](../../scripts/security-check.sh) attacks a running stack over HTTP
the way an outsider would: it creates two throwaway accounts and tries to cross between them,
replay tokens and guess passwords. Run against the production nginx configuration:

```text
PASS  Content-Security-Policy restricts scripts to the app's own origin
PASS  X-Content-Type-Options: nosniff
PASS  Framing blocked (X-Frame-Options or frame-ancestors)
PASS  Referrer-Policy set
PASS  No token: 401 with a JSON error
PASS  Forged token: 401
PASS  Refresh cookie is HttpOnly and SameSite=Strict
PASS  Refresh rotates the token
PASS  Reusing an old refresh token is refused and revokes the newer one too
PASS  A text file named .pdf is rejected (415)
PASS  A real PDF is accepted
PASS  Another user's GET /documents/{id}: 404
PASS  Another user's GET /documents/{id}/file: 404
PASS  Another user's GET /documents/{id}/results: 404
PASS  Another user's GET /documents/{id}/status: 404
PASS  Another user's PATCH /documents/{id}: 404
PASS  Another user's POST /documents/{id}/reprocess: 404
PASS  Another user's DELETE /documents/{id}: 404
PASS  Another user's list doesn't include it
PASS  The owner's file download is marked no-store
PASS  After 5 wrong passwords even the right one is refused (429 + Retry-After)
PASS  AI service endpoints are not exposed through the web entry point

22/22 checks passed
```

Over HTTPS (the production overlay with Caddy) the script also requires HSTS and the `Secure`
cookie flag. Inside the test suite, `UserIsolationTest` does the same for **every** route: it
fails the build if a new endpoint is added without being classified and attacked.

**Dependencies:** `npm audit` (frontend) and `pip-audit` (AI service lock file) report no known
vulnerabilities. The backend has no automated dependency scan yet (OWASP Dependency-Check needs
the NVD database); this is listed under [known gaps](#5-what-this-audit-did-not-cover).

The new frontend was loaded under the production Content-Security-Policy (no inline scripts or
styles) on every page, including the search palette: no violations and no console errors.

## 3. Reading the riskiest code

Read line by line, with the question "what happens under concurrency, failure or a hostile user?":

| Area | Verdict |
|---|---|
| Login, registration, timing | Unknown emails are checked against a dummy hash, so response time doesn't reveal accounts. BCrypt's 72-byte limit is enforced, not silently truncated. Registration races end in 409, not 500. |
| Refresh tokens | Stored hashed; rotation row-locks the token; reuse revokes every session and that revocation stays committed although the request fails (`noRollbackFor`). |
| Login throttling | Counted in PostgreSQL, so shared by instances and restarts. Account deletion is throttled too, so a stolen access token can't be used to guess the password. Client addresses come from `X-Forwarded-For` only via trusted proxies (Tomcat `RemoteIpValve`). |
| Uploads and storage | Type detected from magic bytes; storage keys are generated and validated against a strict pattern before touching the disk; files are written to a temp file and moved atomically; duplicates detected by SHA-256 including a race between two identical uploads. |
| Deletion | Rows first, files after commit, so a failure leaves an orphan file, never a row pointing at nothing. |
| Search | Every statement is scoped by `user_id`; `LIKE` wildcards in user input are escaped. |
| AI boundary | AI service rejects every request when its key is unset; constant-time key comparison; image size capped against decompression bombs. |
| **Job runner** | **Bug found and fixed (below).** |

### The bug: a late worker could finish someone else's job

`JobRunner` recovers jobs left `RUNNING` for longer than `stale-after` (10 minutes), assuming
their worker crashed. But a live job can legitimately take that long: one OCR call and one LLM call,
each allowed up to 5 minutes. With two backend instances:

1. Instance A claims a job and is still waiting on the AI service at minute 10.
2. Instance B decides the job is abandoned, re-queues it and claims it again (attempt 2).
3. A finishes and marks the job DONE, although B is still working on attempt 2.
4. B finishes; `complete()` finds the job no longer RUNNING and throws, which aborted B's whole
   worker cycle and left the rest of its batch stuck until they too went stale.

**Fix** (`fdd3b71`): finishing a job now locks the row and only proceeds if the job is still
`RUNNING` **under the attempt number this worker claimed**; otherwise it logs and leaves the job to
the newer attempt. `stale-after` was raised to 15 minutes, above the slowest live job. The new test
`JobProcessingTest.aLateWorkerLeavesAJobAloneOnceAnotherInstanceHasTakenItOver` reproduces the
takeover; it fails on the old code and passes on the new.

It had never been seen in practice (the demo runs one instance); it was found only by reading the
code with concurrency in mind. Logged as bug #12 in the [bug log](bug-log.md).

## 4. Smaller findings

| Finding | Action |
|---|---|
| Two statements in a draft ADR described bugs that never happened, and one said OCR text is kept when extraction fails (it isn't) | Rewritten from the bug log; the OCR-text gap is now listed in ADR-010 and the roadmap |

## 5. What this audit did not cover

Stated plainly so nobody reads more into it than is there:

- No third-party penetration test, fuzzing or formal threat model review.
- No automated dependency scan for the backend's Maven dependencies.
- No accessibility audit with assistive technology beyond the roles and labels the UI tests use.
- No load test above 20 concurrent users, and none with the real LLM (it is the slow part; the
  offline reader was used to measure Medfold itself).

## Reproduce

```bash
scripts/verify-claims.sh                                   # numbers (about 4 minutes)
docker compose up -d --build && scripts/security-check.sh  # attacks the running stack
```
