# Deploying Medfold

Medfold runs on one Linux server with Docker. Caddy terminates HTTPS and gets its certificate
from Let's Encrypt automatically; nothing else is reachable from outside.

```
internet ──443──► caddy ──► frontend (nginx: SPA + /api proxy) ──► backend ──► db
                                                                     └──► ai-service
```

## 1. Prepare the server

- A Linux server with Docker Engine and the Compose plugin (`docker compose version` ≥ 2.24).
- A DNS `A`/`AAAA` record for your host name (e.g. `records.example.com`) pointing at it.
- Ports **80 and 443** open to the internet (80 is needed for the certificate challenge and
  redirects to HTTPS). Close everything else.

## 2. Configure

```bash
git clone https://github.com/n3h4a/medfold.git
cd medfold
cp .env.example .env
chmod 600 .env
```

Edit `.env` and set at least:

| Variable | Value |
|---|---|
| `MEDFOLD_DOMAIN` | your host name, e.g. `records.example.com` |
| `POSTGRES_PASSWORD` | a long random value: `openssl rand -hex 24` |
| `JWT_SECRET` | `openssl rand -hex 32` |
| `AI_INTERNAL_KEY` | `openssl rand -hex 32` |
| `LLM_PROVIDER` / `LLM_API_KEY` | `anthropic` and your API key to read documents with Claude; `mock` keeps the offline rule-based reader |

The production overlay refuses to start while `MEDFOLD_DOMAIN`, `POSTGRES_PASSWORD`, `JWT_SECRET`
or `AI_INTERNAL_KEY` is empty, and always sends the refresh cookie as `Secure`.

## 3. Start

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Open `https://<MEDFOLD_DOMAIN>`, register, and check **System status** (link in the footer):
backend, database and AI service should all be green. The first start downloads images and the
certificate, which can take a minute or two.

What the overlay changes compared with `docker compose up` for local use:

- adds **Caddy** on ports 80/443 (HTTPS, HTTP→HTTPS redirect, HSTS, compression);
- **unpublishes** the database, backend, AI service and frontend ports;
- requires real secrets and sets `REFRESH_COOKIE_SECURE=true`.

nginx adds a strict Content-Security-Policy and other security headers to every page. The
backend takes the client address from `X-Forwarded-For` only when it comes from an internal proxy
address, which login throttling depends on.

## 4. Back up

```bash
scripts/backup.sh /var/backups/medfold
```

This writes `medfold-<time>.dump` (the database) and `medfold-<time>-uploads.tar.gz` (uploaded
files), readable only by the user running it. Run it daily, for example with cron:

```
17 3 * * * cd /opt/medfold && COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml" scripts/backup.sh /var/backups/medfold
```

Backups contain medical records: copy them to **encrypted** storage off the server, and delete
old ones on a schedule you choose.

## 5. Restore

```bash
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml" \
  scripts/restore.sh /var/backups/medfold/medfold-<time>.dump /var/backups/medfold/medfold-<time>-uploads.tar.gz
```

It asks for confirmation, stops the backend, replaces the database, writes the files back and
starts the backend again. This works on a freshly installed server too: start it once (step 3),
then restore. Test a restore on a spare server now and then; an untested backup is a hope.

## 6. Update

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Database migrations run automatically when the backend starts. Take a backup first.
