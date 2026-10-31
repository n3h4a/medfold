# ADR-001: Authentication with short-lived JWTs and a rotating refresh cookie

**Status:** Accepted

## Context

Medfold stores medical records, so a stolen session is a serious breach. The client is a
single-page app talking to a stateless JSON API; the app should keep users logged in for days
without asking for the password again.

## Decision

- **Access token:** a JWT (HS256) valid for 15 minutes, held only in JavaScript memory and sent as
  `Authorization: Bearer`. Never in `localStorage`, where any injected script could read it.
- **Refresh token:** 32 random bytes in an `HttpOnly`, `SameSite=Strict`, `Secure` cookie scoped to
  `/api/v1/auth`. The server stores only its SHA-256 hash.
- **Rotation with reuse detection:** every refresh issues a new refresh token and revokes the old
  one. Presenting a revoked token again means it was copied, so every session of that user is
  revoked.
- **Passwords:** BCrypt; login attempts with unknown emails take the same time as real ones.
- **Rate limiting:** wrong passwords are counted per account and per client address in the
  database (see [security design](../security-design.md#rate-limiting)).

## Alternatives considered

| Option | Why not |
|---|---|
| Server-side sessions (cookie + session table) | Works well, but every request needs a session lookup and CSRF protection; the API would no longer be stateless |
| Long-lived JWT in `localStorage` | Readable by any XSS; can't be revoked before it expires |
| OAuth/OIDC provider (Keycloak, Auth0) | More moving parts than a single-user-per-account app needs today; still possible later |

## Consequences

- A stolen access token works for at most 15 minutes; a stolen refresh token is detected on its
  next use.
- `SameSite=Strict` plus the narrow cookie path means the refresh endpoint is the only place CSRF
  could matter, and it only rotates tokens.
- The frontend needs refresh-and-retry logic, including for XHR uploads and parallel requests
  (implemented once in `api/client.ts`).
- Logging out on one device doesn't end other devices' 15-minute access tokens.
