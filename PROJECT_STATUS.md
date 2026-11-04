# Project status

*Last updated 2026-10-31.*

| | |
|---|---|
| **Version** | 1.1.0 (see [CHANGELOG](CHANGELOG.md)) |
| **Phase** | Feature-complete for a personal health record; hardening and sharing next |
| **Build** | CI green on `main`: backend, AI service, frontend, deployment config, end-to-end on the Docker stack |
| **Tests** | 479 (230 backend · 109 frontend · 140 AI service), 0 failing; reproduce with `scripts/verify-claims.sh` |
| **Coverage** | Lines 93.6% backend · 94.7% frontend · 97.1% AI service |
| **Security check** | 22/22 external checks pass (`scripts/security-check.sh`); no known vulnerable dependencies (npm, pip) |
| **Try it** | `docker compose up` locally, or a GitHub Codespace ([how](docs/06-deployment/deployment-guide.md#a-public-demo)) |

## Done

- **v0.1 Project setup:** three services, database migrations, auth, CI.
- **v0.5 Core MVP:** upload, OCR and AI extraction, timeline, results compared with the printed range.
- **v1.0 Demo ready:** trends across units, search, corrections that survive reprocessing, account
  export and deletion, login throttling, HTTPS production setup, backups, full documentation.
- **v1.1 Polish and proof:** overview dashboard, visit summary, Ctrl/⌘ K search, dark mode and a
  redesigned UI; a code audit (one concurrency bug found and fixed); scripts that re-measure every
  quoted number and attack a running stack; ADRs 008–010; demo video; Codespaces demo.

Feature by feature, with the tests behind each: [FEATURE_MATRIX.md](FEATURE_MATRIX.md).

## In progress

Nothing half-built is on `main`. The next milestone is **v1.2: trust and recovery**: password reset,
two-factor login and sex- and age-specific ranges. Typo-tolerant search (#3) is done.

## Remaining before calling it "production for other people"

These matter once people other than me rely on it:

1. **Account recovery:** password reset and email verification (today a forgotten password means a
   new account).
2. **Two-factor login.**
3. **Offsite encrypted backups**, automated.
4. **A backend dependency scan** in CI (frontend and AI service are scanned).
5. **An accessibility audit** with a screen reader, beyond the roles and labels the tests check.

## Known issues

| Issue | Impact | Tracking |
|---|---|---|
| OCR text isn't kept when the extraction call fails | Such a document isn't searchable until a retry succeeds | [ADR-010](docs/03-design/adr/ADR-010-ocr-before-ai.md), roadmap |
| Sex- and age-specific ranges aren't parsed | Those results show no comparison rather than a possibly wrong one | [KNOWN_LIMITATIONS](KNOWN_LIMITATIONS.md) |
| Files live on one server's disk | Can't scale out beyond one host yet | Roadmap: object storage |
| The dark theme flashes light for a moment on first load | Cosmetic | The theme is applied after the app starts, because the CSP forbids inline scripts |

All limitations: [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). Bugs found so far and how each is
now guarded: [bug log](docs/05-testing/bug-log.md).

## Future

- **v1.3 Reading more:** more OCR languages, a cloud OCR option, a vision model only for
  low-confidence pages, multi-report PDFs.
- **v1.4 Sharing:** an expiring read-only link for a doctor, a medicine timeline, FHIR export.
- **Platform:** object storage, metrics and tracing, `LISTEN/NOTIFY` instead of polling.

Details: [ROADMAP.md](ROADMAP.md) and the [issues](https://github.com/n3h4a/medfold/issues).
