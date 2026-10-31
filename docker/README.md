# Docker

| File | Purpose |
|---|---|
| [`../docker-compose.yml`](../docker-compose.yml) | Development and demo: the whole app on one machine (`docker compose up -d --build` → http://localhost:3000); `docker compose up -d db` for just the database |
| [`docker-compose.prod.yml`](docker-compose.prod.yml) | Production overlay: adds Caddy (HTTPS), unpublishes internal ports, requires real secrets |
| [`caddy/Caddyfile`](caddy/Caddyfile) | HTTPS entry point: automatic Let's Encrypt certificate, HSTS, compression |
| [`../backend/Dockerfile`](../backend/Dockerfile) | Maven wrapper build → JRE runtime, non-root |
| [`../frontend/Dockerfile`](../frontend/Dockerfile) | Vite build → nginx |
| [`../frontend/nginx.conf`](../frontend/nginx.conf) | SPA routing, `/api` proxy, Content-Security-Policy and security headers |
| [`../ai-service/Dockerfile`](../ai-service/Dockerfile) | Python with Tesseract, non-root, health check |

Each Dockerfile sits next to the code it builds, so its build context is just that service.

## Production

```bash
docker compose -f docker-compose.yml -f docker/docker-compose.prod.yml up -d --build
```

Full guide: [docs/deployment/production.md](../docs/deployment/production.md).

## Volumes

| Volume | Holds |
|---|---|
| `medfold_pgdata` | PostgreSQL data |
| `medfold_uploads` | Uploaded files |
| `caddy_data`, `caddy_config` | Certificates (production) |

`docker compose down` keeps volumes; `down -v` deletes them. Back up with `scripts/backup.sh`.
