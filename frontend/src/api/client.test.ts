import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockApi } from '../test/mockApi.ts'
import { apiGet, apiGetBlob, ApiError, getAccessToken, setAccessToken, setSessionHandlers } from './client.ts'

const invalidToken = () =>
  json(401, { status: 401, code: 'INVALID_TOKEN', message: 'Access token is invalid or expired' })

function authHeader(init: RequestInit | undefined): string | undefined {
  return (init?.headers as Record<string, string> | undefined)?.Authorization
}

/** Handlers whose refresh swaps in a new token, like the real session refresh does. */
function sessionHandlers(refreshResult: boolean | Error = true) {
  const handlers = {
    refresh: vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5))
      if (refreshResult instanceof Error) throw refreshResult
      if (refreshResult) setAccessToken('fresh-token')
      return refreshResult
    }),
    onSessionExpired: vi.fn(),
  }
  setSessionHandlers(handlers)
  return handlers
}

/** /auth/me answers 401 for the stale token and 200 for the fresh one. */
function meAcceptingOnly(token: string) {
  return (init: RequestInit | undefined) =>
    authHeader(init) === `Bearer ${token}` ? json(200, { id: 'user-1' }) : invalidToken()
}

afterEach(() => setSessionHandlers(null))

describe('apiRequest token recovery', () => {
  it('refreshes once and retries when the access token has expired', async () => {
    setAccessToken('stale-token')
    const handlers = sessionHandlers()
    const fetchMock = mockApi({ 'GET /auth/me': meAcceptingOnly('fresh-token') })

    await expect(apiGet('/auth/me')).resolves.toEqual({ id: 'user-1' })

    expect(handlers.refresh).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authHeader(fetchMock.mock.calls[1][1])).toBe('Bearer fresh-token')
    expect(handlers.onSessionExpired).not.toHaveBeenCalled()
  })

  it('retries without refreshing again if another request already refreshed', async () => {
    setAccessToken('stale-token')
    const handlers = sessionHandlers()
    mockApi({
      'GET /auth/me': (init) => {
        // Simulate a parallel request finishing its refresh while this one was in flight.
        if (authHeader(init) === 'Bearer stale-token') setAccessToken('fresh-token')
        return meAcceptingOnly('fresh-token')(init)
      },
    })

    await expect(apiGet('/auth/me')).resolves.toEqual({ id: 'user-1' })
    expect(handlers.refresh).not.toHaveBeenCalled()
  })

  it('ends the session when the refresh cookie is no longer valid', async () => {
    setAccessToken('stale-token')
    const handlers = sessionHandlers(false)
    mockApi({ 'GET /auth/me': invalidToken })

    const error = await apiGet('/auth/me').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('INVALID_TOKEN')
    expect(handlers.onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('ends the session when the server no longer recognises the user', async () => {
    setAccessToken('valid-token')
    const handlers = sessionHandlers()
    mockApi({ 'GET /auth/me': () => json(401, { status: 401, code: 'UNAUTHORIZED', message: 'Authentication required' }) })

    await expect(apiGet('/auth/me')).rejects.toBeInstanceOf(ApiError)
    expect(handlers.refresh).not.toHaveBeenCalled()
    expect(handlers.onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('keeps the session when refresh fails because the network is down', async () => {
    setAccessToken('stale-token')
    const handlers = sessionHandlers(new TypeError('Failed to fetch'))
    mockApi({ 'GET /auth/me': invalidToken })

    await expect(apiGet('/auth/me')).rejects.toBeInstanceOf(TypeError)
    expect(handlers.onSessionExpired).not.toHaveBeenCalled()
  })

  it('does not try to recover on auth endpoints or anonymous requests', async () => {
    const handlers = sessionHandlers()
    mockApi({ 'POST /auth/login': invalidToken, 'GET /documents': invalidToken })

    setAccessToken('stale-token')
    await expect(apiGet('/auth/login')).rejects.toBeInstanceOf(ApiError)
    setAccessToken(null)
    await expect(apiGet('/documents')).rejects.toBeInstanceOf(ApiError)

    expect(handlers.refresh).not.toHaveBeenCalled()
    expect(handlers.onSessionExpired).not.toHaveBeenCalled()
    expect(getAccessToken()).toBeNull()
  })
})

describe('apiGetBlob', () => {
  it('downloads with the token and returns a Blob', async () => {
    setAccessToken('valid-token')
    const fetchMock = mockApi({
      'GET /documents/d/file': () => new Response('%PDF', { status: 200, headers: { 'Content-Type': 'application/pdf' } }),
    })

    const blob = await apiGetBlob('/documents/d/file')

    expect(blob.type).toBe('application/pdf')
    expect(await blob.text()).toBe('%PDF')
    expect(authHeader(fetchMock.mock.calls[0][1])).toBe('Bearer valid-token')
  })

  it('refreshes an expired token before downloading', async () => {
    setAccessToken('stale-token')
    sessionHandlers()
    mockApi({
      'GET /documents/d/file': (init) =>
        authHeader(init) === 'Bearer fresh-token' ? new Response('ok') : invalidToken(),
    })

    expect(await (await apiGetBlob('/documents/d/file')).text()).toBe('ok')
  })

  it('throws ApiError with the server code on failure', async () => {
    setAccessToken('valid-token')
    mockApi({ 'GET /documents/d/file': () => json(404, { status: 404, code: 'DOCUMENT_NOT_FOUND', message: 'x' }) })

    const error = await apiGetBlob('/documents/d/file').catch((caught: unknown) => caught)
    expect((error as ApiError).code).toBe('DOCUMENT_NOT_FOUND')
  })
})
