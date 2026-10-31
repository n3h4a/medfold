# Maintenance

Routine work that keeps a Medfold deployment healthy. Known limitations, the roadmap and release
notes live at the repository root: [KNOWN_LIMITATIONS.md](../../KNOWN_LIMITATIONS.md),
[ROADMAP.md](../../ROADMAP.md), [CHANGELOG.md](../../CHANGELOG.md).

## Schedule

| When | Task | How |
|---|---|---|
| Daily (automatic) | Back up database and files | cron + `scripts/backup.sh` ([deployment guide](../06-deployment/deployment-guide.md#4-back-up)) |
| Daily (automatic) | Delete expired refresh tokens | Built in (`RefreshTokenCleanup`, 03:17) |
| Every 5 min (automatic) | Delete expired login-failure records | Built in (`LoginThrottle`) |
| Every 90 days (automatic) | Renew the HTTPS certificate | Caddy |
| Weekly | Check System status and disk space | `/status`, `df -h`, `docker system df` |
| Monthly | Update dependencies and base images, rebuild | See below |
| Monthly | Copy a backup offsite and test it | [Rollback plan](../06-deployment/rollback-plan.md) on a spare server |
| Yearly, or after any suspected leak | Rotate secrets | See below |

## Updating dependencies

```bash
(cd backend && ./mvnw versions:display-dependency-updates)
(cd frontend && npm outdated)
(cd ai-service && pip list --outdated)
```

Update in a branch, regenerate `ai-service/requirements.txt` if Python dependencies changed
(`pip-compile --strip-extras --output-file=requirements.txt pyproject.toml`), and let CI run every
suite and the end-to-end job before merging. Rebuild images to pick up base-image security fixes:
`docker compose -f docker-compose.yml -f docker/docker-compose.prod.yml up -d --build --pull always`.

## Rotating secrets

| Secret | Effect of rotating | Steps |
|---|---|---|
| `JWT_SECRET` | Every access token becomes invalid; browsers refresh silently with their cookie | Change in `.env`, restart `backend` |
| `AI_INTERNAL_KEY` | None, if both services restart together | Change in `.env`, restart `backend` and `ai-service` |
| `POSTGRES_PASSWORD` | None, after both sides change | `ALTER USER medfold PASSWORD '…'` in the database, change `.env`, restart `backend` |
| `LLM_API_KEY` | None | Create a new key, change `.env`, restart `ai-service`, revoke the old key |

To end every session (e.g. after a suspected breach), rotate `JWT_SECRET` and delete all rows in
`refresh_tokens`; everyone has to log in again.

## Logs and troubleshooting

```bash
docker compose logs --tail 200 backend ai-service
```

| Symptom | Look at |
|---|---|
| Documents stay "Processing" | AI service on System status; `processing_jobs.last_error`; backend logs for "Job … failed" |
| All documents fail with a generic message | `AI_INTERNAL_KEY` differs between services (System status shows the AI service as down) |
| Users logged out unexpectedly | Refresh-token reuse detection in backend logs; clock skew on the server |
| 429 on login | Login throttling working as intended; it clears after 15 minutes |
