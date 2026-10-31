# Implementation notes

Things that weren't obvious, and what we learned building them.

## Making privacy structural, not a convention

The easy mistake in a multi-user app is one `findById` that forgets the owner. Repositories for
user data therefore extend Spring Data's plain `Repository` and declare only owner-scoped methods,
so the unsafe method doesn't exist. To catch new endpoints, `UserIsolationTest` reads Spring's
route table at runtime: every route must be classified, and every route that takes an id is called
by a second user who must get 404 and change nothing. We checked the test itself by deliberately
breaking an endpoint (mutation testing): the build failed as intended.

## PATCH that the isolation test can attack

The isolation test sends `{}` to every owned route. With "required" fields, that would be rejected
by validation (400) before the ownership check, and an attacker's request would never reach it.
PATCH bodies therefore treat every field as optional (omitted = unchanged, empty string = clear),
so `{}` is valid and the 404 path is exercised.

## A queue in PostgreSQL

`UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED LIMIT n) RETURNING id` claims jobs
atomically without workers blocking each other. A test runs two real transactions concurrently
to prove they never get the same job. Enqueueing in the upload's transaction removes the
dual-write problem a separate broker would bring ([ADR-006](../03-design/adr/ADR-006-job-queue.md)).

## Never hold a transaction during AI calls

The worker marks the document `PROCESSING` and commits, calls the AI service with no transaction
open, then saves results in a second short transaction. A slow model can't hold row locks or
exhaust the connection pool.

## Copy, don't compute (AI safety)

The model only copies values. Ranges like `4.0 - 5.6`, `< 200`, `up to 5.0`, `7,400` or `5,6` are
parsed by `LabFlagCalculator` in Java, and anything uncertain (`13.5 H`, `<0.5`, sex-specific
ranges) gets no comparison. Lab test names map to stable keys through a deterministic synonym
table, so trends don't depend on how a model phrased a name.

## Corrections that survive reprocessing

Reprocessing replaces extracted rows but keeps rows the user corrected. Matching a corrected row to
its fresh reading needs a stable identity: lab results use their test key, medicines a stored
`extracted_name`, because the user may have renamed them.

## Refresh tokens and parallel requests

When the access token expires, several requests fail at once. The client shares one refresh
between them (single-flight); otherwise the second refresh would present an already-rotated token
and trigger reuse detection, logging the user out everywhere. XHR uploads go through the same path.

## Charts at real size

The trend chart is hand-written SVG drawn at the container's real pixel width (`ResizeObserver`),
so text stays 11–12 px on phones and wide screens. Points in other units are converted only through
an explicit list of standard conversions per test, and labelled "printed as …".

## Bugs worth remembering

Each found by running the real thing rather than its test doubles; details in the
[bug log](../05-testing/bug-log.md).

- **HTTP/2 upgrade:** Java's HTTP client asked uvicorn to upgrade to HTTP/2, which uvicorn
  rejects for requests with a body. Every real processing call failed while all tests passed,
  because the test server ignored the header. Fixed by pinning HTTP/1.1 and asserting the header
  in tests.
- **Empty Caddy setting:** an empty optional e-mail made Caddy reject its config; found by
  starting the production overlay, not by validating it.
- **Orphaned dev processes:** stopping `npm` or `mvnw` left Vite and the Spring Boot JVM running;
  fixed with process groups.
