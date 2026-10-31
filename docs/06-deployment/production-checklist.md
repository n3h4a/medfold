# Production checklist

Go through this before opening a deployment to real users, and again after major changes.
Setup steps are in the [deployment guide](deployment-guide.md).

## Secrets and configuration

- [ ] `.env` is on the server only, mode `600`, not in git.
- [ ] `POSTGRES_PASSWORD`, `JWT_SECRET`, `AI_INTERNAL_KEY` are long random values (`openssl rand -hex 32`),
      unique to this deployment.
- [ ] `MEDFOLD_DOMAIN` is set and DNS points to the server.
- [ ] Started with `docker/docker-compose.prod.yml` (secure cookie, no internal ports published).
- [ ] `LLM_PROVIDER` chosen deliberately; with `anthropic`, users are told page text is sent to
      Anthropic ([privacy](../01-research/privacy-and-regulation.md)).

## Network

- [ ] Only ports 80 and 443 are open in the firewall; SSH restricted to keys.
- [ ] `https://<domain>` has a valid certificate; `http://` redirects to HTTPS.
- [ ] Response headers include `Strict-Transport-Security` and `Content-Security-Policy`
      (`curl -sI https://<domain>`).
- [ ] Ports 5432, 8000 and 8080 are **not** reachable from outside.

## Health

- [ ] System status shows backend, database and AI service green.
- [ ] Upload a sample from `tests/sample-pdfs/` and see it reach Ready; then delete it.
- [ ] `docker compose ps` shows every service healthy and `restart: unless-stopped`.

## Backups

- [ ] `scripts/backup.sh` runs daily from cron and writes owner-only files.
- [ ] Backups are copied off the server to encrypted storage, with a retention period.
- [ ] A restore has been tested on a spare server ([rollback plan](rollback-plan.md)).

## Operations

- [ ] Someone receives the server's disk-space and uptime alerts.
- [ ] The [maintenance tasks](../07-maintenance/maintenance.md) have an owner.
- [ ] Private vulnerability reporting is enabled on the repository ([SECURITY.md](../../SECURITY.md)).
