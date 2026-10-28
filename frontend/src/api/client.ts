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

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
}

export async function apiRequest<T>(path: string, { method = 'GET', body }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  })
  const responseBody = await parseBody(response)
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
