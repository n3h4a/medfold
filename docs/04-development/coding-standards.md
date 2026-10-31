# Coding standards

The rules this codebase follows. Most are enforced by tests or CI; the rest by review.

## All services

- **Match the surrounding code.** Naming, structure and comment density follow what's already there.
- **Comments explain why,** not what. Public classes and non-obvious methods get one short doc comment.
- **Errors** use one shape everywhere, `{status, code, message, fields?, details?}`, with a stable
  `code` for clients and a plain-language `message` for people. Internal details go to logs only.
- **Time comes from an injected clock** (`Clock` in Java), so tests control it.
- **No secrets in code or logs.** Configuration comes from environment variables (`.env.example`
  documents every one).

## Backend (Java 21, Spring Boot)

- **Package by feature** (`document`, `records`, `search`, …), not by layer.
- **Owner-scoped data access.** Repositories for user data extend Spring Data `Repository` and
  declare only methods like `findByIdAndUserId`; no `findById`/`findAll`. Services take the user
  id from the token, never from the request. *Enforced by* `UserIsolationTest`.
- **Every new endpoint** is classified in `UserIsolationTest` (PUBLIC, SELF, COLLECTION,
  OWNED_BY_ID); an unclassified route fails the build.
- **Entities guard their own state transitions** (`markProcessing()`, `fail(…)`) and reject invalid ones.
- **DTOs are records**; entities never leave the service layer.
- **Validation** with Bean Validation on request records; messages written for users.
- **Transactions are short.** Never call the AI service or other slow I/O inside one.
- **Migrations:** Flyway only, never edit an applied migration. Hibernate runs in `validate` mode.
- **Tests:** JUnit 5, AssertJ, Testcontainers PostgreSQL (no H2), MockMvc for the API. Test names
  are sentences: `anotherUserGetsNotFoundOnEveryOwnedEndpointAndChangesNothing`.
- Style: 4-space indent; imports grouped `java`, `org`, `com.medfold`, then `jakarta`, as in existing files.

## Frontend (TypeScript, React)

- **Typed throughout;** no `any` in the codebase, unused locals and parameters are errors. `tsc -b`
  and `oxlint` run in CI.
- **Feature folders** under `src/features/`; `src/api/` is the only code that calls the backend.
- **Server state in TanStack Query;** no global client store. The access token lives in memory only.
- **Accessible by default:** real buttons and links, labelled inputs, roles on live regions, text
  alternatives for charts. Tests query by role and label, as users perceive the page.
- **Tests:** Vitest + Testing Library against a mocked `fetch`, through the real router and providers.
- Tailwind utility classes; no custom CSS beyond theme tokens in `index.css`.

## AI service (Python 3.12, FastAPI)

- **Pydantic models are contracts.** The extraction schema forbids unknown fields and has no field
  for diagnosis, risk or advice; a test scans for such names.
- **Model output is untrusted:** validate, safety-check, and keep the raw output for audit.
- **Providers behind interfaces** (`LlmProvider`, `OcrProvider`); switching is configuration only.
- **CPU-bound work** (PDF parsing, OCR) runs in a thread pool, never on the event loop.
- **Tests** generate PDFs and images in memory; no network calls (scripted LLM, fake SDK client).
- PEP 8, type hints on public functions.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat(backend): …`, `fix(frontend): …`,
  `docs: …`, `test: …`, `ci: …`, `build: …`, `chore: …`. The body explains why.
- One logical change per commit; tests pass at every commit.
- A bug fix includes a test that fails without it.
- Design changes come with an [ADR](../03-design/adr/README.md).
