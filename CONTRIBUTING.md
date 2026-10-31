# Contributing

Thanks for your interest! Issues and pull requests are welcome.

## Getting started

```bash
cp .env.example .env
scripts/start-dev.sh          # PostgreSQL in Docker + AI service, backend, frontend
scripts/seed-demo-data.sh     # demo@medfold.local / medfold-demo-2026
```

Prerequisites and alternatives (everything in Docker, one service at a time) are in
[docs/deployment](docs/deployment/README.md). Read [docs/architecture](docs/architecture/README.md)
first; the [ADRs](docs/decisions/README.md) explain why things are the way they are.

## Running the checks

CI runs all of these on every pull request; please run the ones for what you changed.

```bash
(cd backend && ./mvnw test)                     # needs Docker (Testcontainers)
(cd ai-service && .venv/bin/pytest)             # OCR tests need Tesseract, otherwise skipped
(cd frontend && npm run lint && npm test && npm run build)
npx newman run postman/medfold.postman_collection.json \
  -e postman/medfold.local.postman_environment.json --working-dir .   # with the app running
```

## Conventions

- **Commits** follow [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(backend): …`, `fix(frontend): …`, `docs: …`, `test: …`, `ci: …`, `chore: …`. The body
  explains *why*. One logical change per commit.
- **Branches:** `feat/<topic>`, `fix/<topic>`; pull requests target `main`.
- **Tests:** new behaviour comes with tests; a bug fix comes with a test that fails without it.
- **Privacy:** every new user-data table carries `user_id`; repositories expose only owner-scoped
  queries (no `findById`/`findAll`); every new endpoint must be classified in `UserIsolationTest`.
- **AI output:** never add a field for diagnosis, risk or advice to the extraction schema; values
  are copied, comparisons computed by the backend ([ADR-007](docs/decisions/ADR-007-ai-safety-boundary.md)).
- **Database:** never edit an applied Flyway migration; add a new one
  ([database/README.md](database/README.md#migrations)).
- **Errors** use the standard `{status, code, message}` shape with plain-language messages.
- **Style:** match the surrounding code. Java uses 4 spaces; TypeScript is checked by `oxlint` and
  `tsc`; Python follows PEP 8.
- **Big changes:** open an issue first; a design change deserves a new ADR in `docs/decisions/`.

## Reporting bugs and security issues

Use the issue templates for bugs and feature ideas. Report security issues privately as described
in [SECURITY.md](SECURITY.md).
