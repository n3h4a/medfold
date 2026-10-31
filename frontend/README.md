# Frontend (React)

The web app: upload, timeline, document viewer with extracted results, trends, search, editing,
account. React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, React Router.

## Run

```bash
npm install
npm run dev        # http://localhost:5173, /api is proxied to the backend on :8080
```

Set `BACKEND_URL` to proxy somewhere else. In Docker, the built app is served by nginx
([`nginx.conf`](nginx.conf)), which also forwards `/api` and sends the security headers.

## Test and build

```bash
npm test           # Vitest + Testing Library, 95 tests against a mocked API
npm run lint       # oxlint
npm run build      # type-check (tsc -b) and production bundle in dist/
```

## Layout

```
src/
├── api/           typed API client: token refresh and retry (fetch and XHR uploads), endpoints
├── auth/          session context, login/register pages, protected and guest routes
├── app/           router, layout, providers
├── features/
│   ├── documents/ timeline, upload queue, detail page, file viewer, results, editing, search
│   ├── labs/      test list, trend chart and unit conversion
│   ├── medications/
│   ├── account/   data export, account deletion
│   └── status/    system status
├── components/    shared form controls
└── test/          test setup, API mocking, render helpers
```

**Notes:** the access token lives only in memory; the refresh token is an HttpOnly cookie the
code never sees. Files are fetched with the token and shown through `blob:` URLs. Pages poll only
while a document is processing. The trend chart is hand-written SVG: one series, hover and
keyboard tooltips, drawn at real pixel width.
