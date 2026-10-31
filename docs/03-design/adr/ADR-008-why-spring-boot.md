# ADR-008: Why Spring Boot (Java) for the product API

**Status:** Accepted · **Related:** [ADR-005](ADR-005-service-split.md) splits product and AI;
this record is about the product side alone.

## Context

The backend is the only public service. Everything that can go wrong with medical data goes
wrong here: one user seeing another user's report, a stolen session that keeps working, a
half-saved upload, a background job that runs twice. The language and framework had to make
those mistakes hard to write and easy to test, more than they had to be quick to start with.

What the backend has to do:

- Authenticate with short-lived JWTs and rotate refresh tokens with reuse detection.
- Wrap uploads, job claims, corrections and account deletion in real transactions, including
  one that must stay committed even though the request fails (revoking every session when a
  refresh token is reused).
- Own the schema: migrations, constraints, partial unique indexes.
- Be tested against a real PostgreSQL, not a mock, because the job queue and search are SQL.

## Decision

**Java 21 with Spring Boot**, using:

| Need | What Spring gives | Where it shows up |
|---|---|---|
| JWT auth without writing crypto | Spring Security OAuth2 resource server | `config/SecurityConfig.java`, `auth/JwtConfig.java` |
| Transactions with precise rollback rules | `@Transactional(noRollbackFor = …)`, `TransactionTemplate` | `RefreshTokenService.rotate`, `JobRunner` (no transaction held during AI calls) |
| Owner-scoped data access | Spring Data repositories declared from plain `Repository` | Repositories have no `findById`, only `findByIdAndUserId` |
| Schema as code | Flyway + Hibernate `ddl-auto: validate` | `db/migration/V1`–`V7` |
| Tests on the real database | Testcontainers, MockMvc | 220+ backend tests, including `UserIsolationTest` |
| Config that fails fast | `@ConfigurationProperties` + validation | A missing JWT secret stops startup |

Java 21 records keep DTOs short, and the type system catches a whole class of mistakes
(a `UUID` user id can't be passed where a document id goes, a `LabFlag` can't be a typo).

## Alternatives considered

| Option | Why not |
|---|---|
| **Node.js (Express / NestJS)** | Same language as the frontend is attractive, but transactions, row locks and auth would be assembled from several libraries. NestJS gets closer but its ORM story (TypeORM/Prisma) is weaker for `SELECT … FOR UPDATE SKIP LOCKED` and partial indexes. |
| **Python (Django / FastAPI)** | One language for backend and AI. Django is strong; I chose to keep the AI service small and stateless and to put the strictest rules in the most strictly typed code. |
| **Go** | Fast and small, but auth, migrations, validation and repository scoping would all be hand-built. More code to get wrong in exactly the places that matter. |
| **Kotlin + Spring** | Equally good. Java was chosen because it is what most reviewers of this project read fluently. |

## Consequences

- **Good:** the riskiest rules (ownership, token rotation, job claiming) are short, declarative
  and covered by tests that run against PostgreSQL. The audit in
  [code-audit.md](../../05-testing/code-audit.md) found one concurrency bug, in the job
  runner; it was fixed with a row lock and a one-line ownership check.
- **Cost: weight.** The JVM starts in about 6 seconds in development and uses several hundred MB
  of memory, far more than a Go or Node service. Fine for one container; it would matter for
  scale-to-zero hosting.
- **Cost: defaults you don't see.** The worst bug of the project came from one: Java's
  `HttpClient` speaks HTTP/2 by default, sends an `Upgrade: h2c` header, and uvicorn rejected every
  upload with 400 while all unit tests passed (bug #1 in the [bug log](../../05-testing/bug-log.md)).
  The base image also had a user with UID 1000 already, which broke the Dockerfile (bug #3).
- **Cost: two ecosystems.** Java here and Python in the AI service means two build systems and two
  dependency trees for CI and Dependabot-style updates to watch.
