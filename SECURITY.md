# Security

Medfold stores medical records, so security and privacy are design constraints, not features.

## Reporting a vulnerability

Please don't open a public issue. Use GitHub's
[private vulnerability reporting](https://github.com/n3h4a/medfold/security/advisories/new) for this
repository. You'll get a reply within a week.

## Authentication and sessions

- **Passwords** are hashed with BCrypt; the 72-byte BCrypt limit is enforced, not silently truncated.
- **Access tokens** are JWTs (HS256) valid for 15 minutes, kept in memory only, never in
  `localStorage`. The signing secret must be at least 32 characters; the app refuses to start without it.
- **Refresh tokens** are random, stored server-side only as SHA-256 hashes, sent in an `HttpOnly`,
  `SameSite=Strict`, `Secure` cookie scoped to `/api/v1/auth`, and rotated on every use. Reusing a
  rotated token revokes all of the user's sessions. Expired tokens are deleted daily.
- Details and alternatives: [ADR-001](docs/decisions/ADR-001-authentication.md).

## Rate limiting

Wrong passwords are counted in a 15-minute sliding window: at most **5 per account** and **30 per
client address**; beyond that, attempts get `429` with `Retry-After` before any password is
checked. Unknown emails are counted like real ones and take the same time to reject, so neither
the limit nor timing reveals which accounts exist. Counts are stored in PostgreSQL as hashes, so
limits hold across restarts and instances. The password confirmation for deleting an account is
limited the same way. Behind the proxy, the client address comes from `X-Forwarded-For` trusted
only from internal addresses.

## Data isolation

- Every repository for user data exposes only owner-scoped queries such as
  `findByIdAndUserId`; there is no `findById` to misuse.
- Another user's resource returns the same `404` as a missing one, so ids can't be probed.
- `UserIsolationTest` reads Spring's route table: every endpoint must be classified, and every
  endpoint that addresses a resource by id is attacked by a second user, who must get `404` and
  change nothing. A new endpoint without classification fails the build.

## File handling

- File types are detected from content (magic bytes), not names or `Content-Type`; only PDF,
  JPEG and PNG are accepted, up to 10 MB.
- Storage keys are generated server-side (`{userId}/{uuid}.{ext}`), validated, and resolved inside
  the storage root, which rules out path traversal.
- Files are served with `Cache-Control: no-store, private` and shown through short-lived `blob:` URLs.
- The AI service guards against decompression bombs and oversized images, and caps pages.

## Transport and browser

- Production runs behind Caddy with automatic HTTPS, HTTP→HTTPS redirects and HSTS; only ports
  80/443 are published.
- nginx sends a strict `Content-Security-Policy` (same-origin scripts, styles and requests;
  `blob:` only for the file viewer; no framing by other sites), `X-Content-Type-Options`,
  `Referrer-Policy` and `Permissions-Policy`, and hides its version.
- The AI service is never exposed publicly and rejects every call without the internal key.

## AI safety

The model can only fill a schema without fields for diagnosis or advice; values are copied, not
computed; free text is checked for advice and judgment wording; document text is treated as data,
never instructions. See [ADR-007](docs/decisions/ADR-007-ai-safety-boundary.md).

## Privacy

- Users can download all their data and delete their account; deletion removes every row and file.
- Errors shown to users never contain internal details; those go to logs only.
- Backups contain medical data: `scripts/backup.sh` writes them owner-readable only, and the
  deployment guide requires encrypted offsite storage.
- With `LLM_PROVIDER=anthropic`, page text is sent to Anthropic's API for extraction. The offline
  `mock` provider keeps everything on your server.

## Not yet covered

No multi-factor authentication, no email verification or password reset by email, no encryption
of files at rest beyond the host's disk encryption. See [ROADMAP.md](ROADMAP.md).
