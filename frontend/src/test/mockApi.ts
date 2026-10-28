import { vi } from 'vitest'

type Handler = (init: RequestInit | undefined) => Response | Promise<Response>

export function json(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export const emptyPage = { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 }

/** Requests every logged-in page makes; tests override them when they care. */
const defaultHandlers: Record<string, Handler> = {
  'GET /documents': () => json(200, emptyPage),
  'GET /health': () => json(200, { service: 'medfold-backend', status: 'UP', database: 'UP', schemaVersion: '3' }),
}

/**
 * Stubs fetch with handlers keyed by "METHOD /path" (path without the /api/v1 prefix and query string).
 * Unmatched requests get a 404 so a missing handler fails loudly.
 */
export function mockApi(overrides: Record<string, Handler>) {
  const handlers = { ...defaultHandlers, ...overrides }
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const path = url.replace(/^.*\/api\/v1/, '').split('?')[0]
    const key = `${init?.method ?? 'GET'} ${path}`
    const handler = handlers[key]
    return handler ? handler(init) : json(404, { status: 404, code: 'NOT_FOUND', message: `No mock for ${key}` })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const demoUser = { id: 'user-1', email: 'asha@example.com', fullName: 'Asha Rao' }

export function authResponse(user = demoUser) {
  return { accessToken: 'access-token-1', tokenType: 'Bearer', expiresAt: '2030-01-01T00:00:00Z', user }
}

export const noSession = () =>
  json(401, { status: 401, code: 'INVALID_REFRESH_TOKEN', message: 'Session has expired. Please log in again' })

export const health = () => json(200, { service: 'medfold-backend', status: 'UP', database: 'UP', schemaVersion: '2' })
