# Bug log

Real bugs found during development, with root cause, fix and the test that now guards against
each. Most were found by running the real system end to end rather than its test doubles, which is
why the CI end-to-end job and the manual checklist exist.

| # | Found | Symptom | Root cause | Fix | Guarded by |
|---|---|---|---|---|---|
| 1 | 2026-10-31, seeding demo data against the real AI service | Every document FAILED; all tests green | Java's `HttpClient` defaults to HTTP/2 and sends `Upgrade: h2c` over plain HTTP; uvicorn rejects that with 400 for requests with a body. The tests' stand-in server ignored the header | Pin HTTP/1.1 for both AI service clients (`a2dfaaa`) | `AiServiceClientTest` asserts no `Upgrade` header; CI end-to-end job |
| 2 | 2026-10-31, deploying the production overlay | Caddy restarted in a loop | An empty optional `ACME_EMAIL` produced `email` with no argument, which Caddy refuses | Removed the optional setting (`22a1b0f`) | Production overlay started and checked over HTTPS before release |
| 3 | 2026-10-30, building the backend runtime image | Image build failed at `useradd` | UID 1000 already exists in the Ubuntu-based Temurin image | Use UID 10001 | CI end-to-end job builds the image |
| 4 | 2026-10-31, reviewing reprocessing | A corrected medicine that the user had renamed appeared twice after reprocessing | Corrections were matched to new readings by their current name | Store `extracted_name` (V7) and match on it (`60241ad`) | `DocumentPipelineTest.renamedCorrectionsAreStillRecognisedWhenReprocessing` |
| 5 | 2026-10-31, rendering diagrams | The processing sequence diagram showed an error on GitHub | Semicolons in a Mermaid message end the statement | Reworded; all diagrams now rendered by `assets/diagrams/render.sh` | Rendering fails on syntax errors |
| 6 | 2026-10-31, screenshots | "1 pages" on single-page documents | Plural always used | `ace5be3` | `DocumentDetailPage.test.tsx` "says \"1 page\" for a single page" |
| 7 | 2026-10-31, demo data | Summaries said "This is a imaging report" | Article always "a" | `6ace9b7` | `test_mock_llm.py::test_summaries_use_the_right_article` |
| 8 | 2026-10-31, demo data | The X-ray report's facility was read as the page footer | The footer text contained "laboratory", a facility keyword | Reworded the synthetic footer | Sample generator output checked by the seed script |
| 9 | 2026-10-31, `stop-dev` | Vite and the Spring Boot JVM kept running after stopping | Stopping `npm` / `mvnw` doesn't stop the processes they start | Each service in its own process group; stop the group (`d8136d1`) | Verified start → stop leaves no processes |
| 10 | 2026-10-31, regenerating samples | Regenerated PDFs counted as new uploads | reportlab embeds a timestamp, so bytes and hashes changed | Deterministic output (`invariant=1`) | Generator run twice gives identical hashes |
| 11 | 2026-10-31, reviewing known limitations | Login limits reset on every restart and weren't shared between backend instances | Failure counts were kept in memory | Counts in PostgreSQL (`6974cfd`) | `LoginThrottleTest.countsAreSharedByEveryInstance` |
