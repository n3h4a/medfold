# Product requirements

What v1.0 does, with the priority each requirement had when the MVP was planned
(**Must**, **Should**, **Could**, MoSCoW) and its status. The problem, audience and goals are in
the [project overview](../00-project/overview.md); how each requirement is verified is in
[use cases](use-cases.md).

## Functional requirements

| # | Requirement | Priority | Status |
|---|---|---|---|
| F1 | Register, log in, stay logged in across reloads, log out | Must | Done |
| F2 | Upload PDF, JPG or PNG up to 10 MB, several at once, with progress | Must | Done |
| F3 | Reject unsupported or disguised files, and files already uploaded | Must | Done |
| F4 | Read text: exact from digital PDFs, OCR for scans and photos | Must | Done |
| F5 | Extract type, date, doctor, facility, lab results, medicines, term definitions | Must | Done |
| F6 | Show the original next to the extracted data | Must | Done |
| F7 | Compare each lab value with the range printed on the same report | Must | Done |
| F8 | Timeline of documents by the date of the record | Must | Done |
| F9 | Each test over time across reports, with unit conversion | Should | Done |
| F10 | Search any word across details, text, tests and medicines | Should | Done |
| F11 | Correct or remove anything that was misread; corrections survive reprocessing | Should | Done |
| F12 | Process a document again; see why processing failed | Should | Done |
| F13 | Medicines across all documents | Should | Done |
| F14 | Download all data; delete the account | Should | Done |
| F15 | Share records with a doctor | Could | [Planned](https://github.com/n3h4a/medfold/issues/9) |
| F16 | Password reset, two-factor authentication | Could | [Planned](https://github.com/n3h4a/medfold/issues/1) |

## Non-functional requirements

| # | Requirement | Target | Measured |
|---|---|---|---|
| N1 | Isolation: a user can never read or change another user's data | Every route verified | 25 of 25 routes ([test plan](../05-testing/test-plan.md)) |
| N2 | AI never interprets or advises | No such schema field; wording checked | Enforced by tests |
| N3 | Read latency | p95 < 200 ms at 20 users | 6.5–11 ms ([performance](../05-testing/performance-testing.md)) |
| N4 | Upload responds immediately; processing in the background | Upload p95 < 500 ms | 15.7 ms |
| N5 | Survives AI outages and restarts | Retries, no lost jobs | Tested (pipeline and job tests) |
| N6 | Secure by default | HTTPS, CSP, hashed secrets, rate limiting | [Security design](../03-design/security-design.md) |
| N7 | Accessible | Keyboard and screen-reader friendly controls; text alternatives for charts | Labels and roles tested in UI tests |
| N8 | One-command setup | `docker compose up` | Verified in CI |
| N9 | Test coverage | > 85% of lines per service | 92.6–93.5% |

## Out of scope for v1.0

Medical interpretation or advice, hospital integrations, multiple people per account, a mobile
app, offline use. See [ROADMAP.md](../../ROADMAP.md).
