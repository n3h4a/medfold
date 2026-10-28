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

async function parseBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
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

async function send(path: string, method: string, body: unknown, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  return fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  })
}

function errorCode(body: unknown): string | undefined {
  return typeof body === 'object' && body !== null ? (body as { code?: string }).code : undefined
}

/** Auth endpoints answer 401 for wrong passwords or dead refresh cookies; never refresh-and-retry those. */
function isAuthEndpoint(path: string): boolean {
  return path.startsWith('/auth/') && path !== '/auth/me'
}

export async function apiRequest<T>(path: string, { method = 'GET', body }: RequestOptions = {}): Promise<T> {
  const sentToken = accessToken
  let response = await send(path, method, body, sentToken)
  let responseBody = await parseBody(response)

  const canRecover = response.status === 401 && sentToken !== null && sessionHandlers !== null && !isAuthEndpoint(path)
  if (canRecover && errorCode(responseBody) === 'INVALID_TOKEN') {
    // Another request may already have refreshed while this one was in flight.
    const refreshed = accessToken !== sentToken || (await sessionHandlers!.refresh())
    if (refreshed && accessToken) {
      response = await send(path, method, body, accessToken)
      responseBody = await parseBody(response)
    }
  }
  if (canRecover && response.status === 401) {
    sessionHandlers!.onSessionExpired()
  }

  if (!response.ok) {
    throw new ApiError(response.status, responseBody)
  }
  return responseBody as T
}

export function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path)
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, { method: 'POST', body })
}
