# Contributing

Thanks for your interest! Issues and pull requests are welcome.

## Getting started

```bash
cp .env.example .env
scripts/start-dev.sh          # PostgreSQL in Docker + AI service, backend, frontend
scripts/seed-demo-data.sh     # demo@medfold.local / medfold-demo-2026
```

Prerequisites and alternatives (everything in Docker, one service at a time) are in
[docs/06-deployment](docs/06-deployment/README.md). Read [the architecture](docs/03-design/architecture.md)
first; the [ADRs](docs/03-design/adr/README.md) explain why things are the way they are.

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

The full list is in [coding standards](docs/04-development/coding-standards.md). The essentials:

- [Conventional Commits](https://www.conventionalcommits.org/), one logical change per commit,
  tests passing at every commit; a bug fix includes a test that failed before.
- Every new endpoint is classified in `UserIsolationTest`; user data is only read through
  owner-scoped queries.
- No extraction field for diagnosis, risk or advice ([ADR-007](docs/03-design/adr/ADR-007-ai-safety-boundary.md)).
- Database changes are new Flyway migrations ([database](database/README.md#migrations)).
- Design changes come with an [ADR](docs/03-design/adr/README.md).

By participating you agree to the [code of conduct](CODE_OF_CONDUCT.md).

## Reporting bugs and security issues

Use the issue templates for bugs and feature ideas. Report security issues privately as described
in [SECURITY.md](SECURITY.md).
