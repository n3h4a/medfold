# Security policy

Medfold stores medical records, so security reports are taken seriously.

## Reporting a vulnerability

Please **don't open a public issue**. Report privately through GitHub's
[private vulnerability reporting](https://github.com/n3h4a/medfold/security/advisories/new). Include
the steps to reproduce and the impact you see. You'll get a reply within a week, and a fix or a
plan within 30 days for confirmed issues. Please give us a chance to fix the problem before
disclosing it.

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x | Yes |
| < 1.0 | No |

## How Medfold is secured

- Short-lived JWT access tokens in memory; rotating refresh tokens in HttpOnly, SameSite=Strict
  cookies, stored only as hashes, with reuse detection.
- BCrypt passwords; login and account-deletion attempts rate-limited per account and per address.
- Every query scoped to the owner; a test attacks every endpoint as another user.
- File types checked from content; server-generated storage keys; no caching of medical files.
- HTTPS with HSTS, a strict Content-Security-Policy, internal services never exposed.
- The AI can't produce diagnosis or advice fields, and its wording is checked.

The full design, including what isn't covered yet, is in
[docs/03-design/security-design.md](docs/03-design/security-design.md).
