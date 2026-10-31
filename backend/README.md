# Backend (Spring Boot)

The only public backend: accounts, documents, storage, the processing queue, records, search.
Java 21, Spring Boot 4, PostgreSQL with Flyway, Spring Security as an OAuth2 resource server (JWT).

## Run

```bash
docker compose up -d db          # from the repository root
./mvnw spring-boot:run           # http://localhost:8080/api/v1/health
```

Configuration comes from environment variables or the repository-root `.env`; see
[environment variables](../docs/06-deployment/environment-variables.md) and
[`application.yml`](src/main/resources/application.yml).

## Test

```bash
./mvnw test        # 222 tests; Testcontainers starts a throwaway PostgreSQL, so Docker must run
```

Highlights: `UserIsolationTest` (every route classified, every owned route attacked by a second
user), `DocumentPipelineTest` (upload → worker → a stand-in AI service on a real port → database),
`JobProcessingTest` (a real two-transaction `SKIP LOCKED` race), `AiServiceClientTest` (error
mapping, timeouts, wire format), `LabFlagCalculatorTest` (38 printed-range cases).

## Layout

```
src/main/java/com/medfold/
├── auth/         register, login, JWT, rotating refresh tokens, login throttling
├── user/         accounts
├── document/     upload, type detection, timeline, file streaming, corrections, reprocess
├── storage/      StorageService + local filesystem implementation
├── processing/   job queue (SKIP LOCKED), worker, scheduler, result writer
├── ai/           AI service client, error mapping, availability check
├── records/      lab results, medicines, trends, LabFlagCalculator, corrections
├── search/       owner-scoped search with snippets
├── account/      data export, account deletion
├── health/       /api/v1/health
├── common/       error format, exception handling, paging
└── config/       security, scheduling, clock
src/main/resources/db/migration/   Flyway migrations V1–V7 (see database/README.md)
```

**Conventions:** repositories for user data expose only owner-scoped methods; services take the
user id from the token; errors use `ApiException` with a stable code and a plain message; time
comes from an injected `Clock` so tests control it.
