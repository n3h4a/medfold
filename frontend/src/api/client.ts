const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/** Shape of every error body returned by the backend. */
export interface ApiErrorBody {
  status: number
  code: string
  message: string
  fields?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, body: unknown) {
    super(`Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }

  private get errorBody(): Partial<ApiErrorBody> {
    return typeof this.body === 'object' && this.body !== null ? (this.body as Partial<ApiErrorBody>) : {}
  }

  get code(): string | undefined {
    return this.errorBody.code
  }

  get serverMessage(): string | undefined {
    return this.errorBody.message
  }

  get fields(): Record<string, string> {
    return this.errorBody.fields ?? {}
  }
}

// The access token lives only in memory. The refresh token is an httpOnly cookie the browser
// sends to /api/v1/auth automatically; JavaScript never sees it.
let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

/**
 * How the client recovers from an expired access token. Registered by AuthProvider so this
 * module does not depend on React or on the auth API (which itself uses this client).
 */
export interface SessionHandlers {
  /** Gets a new access token from the refresh cookie. Resolves true if the session is still valid. */
  refresh: () => Promise<boolean>
  /** Called once it is certain the session has ended, so the app can return to login. */
  onSessionExpired: () => void
}

let sessionHandlers: SessionHandlers | null = null

export function setSessionHandlers(handlers: SessionHandlers | null): void {
  sessionHandlers = handlers
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
}

/** Status and parsed body of a finished request, independent of fetch or XMLHttpRequest. */
export interface RawResponse {
  status: number
  body: unknown
}

export const API_BASE = API_BASE_URL

export function parseText(text: string): unknown {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function errorCode(body: unknown): string | undefined {
  return typeof body === 'object' && body !== null ? (body as { code?: string }).code : undefined
}

/** Auth endpoints answer 401 for wrong passwords or dead refresh cookies; never refresh-and-retry those. */
function isAuthEndpoint(path: string): boolean {
  return path.startsWith('/auth/') && path !== '/auth/me'
}

/**
 * Runs a request and recovers from an expired access token: on 401 INVALID_TOKEN it refreshes
 * once (shared with any parallel callers) and retries with the new token. If the session has
 * really ended, the app is told so it can return to login.
 *
 * `send` performs the actual HTTP call with the given token, so fetch and XMLHttpRequest
 * (needed for upload progress) share the same recovery rules.
 */
export async function withAuthRecovery(
  path: string,
  send: (token: string | null) => Promise<RawResponse>,
): Promise<RawResponse> {
  const sentToken = accessToken
  let response = await send(sentToken)

  const canRecover = response.status === 401 && sentToken !== null && sessionHandlers !== null && !isAuthEndpoint(path)
  if (canRecover && errorCode(response.body) === 'INVALID_TOKEN') {
    // Another request may already have refreshed while this one was in flight.
    const refreshed = accessToken !== sentToken || (await sessionHandlers!.refresh())
    if (refreshed && accessToken) {
      response = await send(accessToken)
    }
  }
  if (canRecover && response.status === 401) {
    sessionHandlers!.onSessionExpired()
  }
  return response
}

async function fetchRaw(path: string, method: string, body: unknown, token: string | null): Promise<RawResponse> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  })
  return { status: response.status, body: parseText(await response.text()) }
}

export async function apiRequest<T>(path: string, { method = 'GET', body }: RequestOptions = {}): Promise<T> {
  const response = await withAuthRecovery(path, (token) => fetchRaw(path, method, body, token))
  if (response.status < 200 || response.status >= 300) {
    throw new ApiError(response.status, response.body)
  }
  return response.body as T
}

export function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path)
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, { method: 'POST', body })
}

export function apiDelete(path: string): Promise<null> {
  return apiRequest<null>(path, { method: 'DELETE' })
}
