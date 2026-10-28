import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { authApi } from '../api/auth.ts'
import { getAccessToken } from '../api/client.ts'
import { authResponse, demoUser, health, json, mockApi, noSession } from '../test/mockApi.ts'
import { renderApp } from '../test/renderApp.tsx'
import { refreshSession } from './session.ts'

describe('session restore', () => {
  it('sends visitors without a session to the login page', async () => {
    mockApi({ 'POST /auth/refresh': noSession })

    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Log in to Medfold' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('restores a session from the refresh cookie and shows the app', async () => {
    mockApi({ 'POST /auth/refresh': () => json(200, authResponse()), 'GET /health': health })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Your documents' })).toBeInTheDocument()
    expect(screen.getByText(demoUser.email)).toBeInTheDocument()
    expect(getAccessToken()).toBe('access-token-1')
  })

  it('redirects logged-in users away from the login page', async () => {
    mockApi({ 'POST /auth/refresh': () => json(200, authResponse()), 'GET /health': health })

    const { router } = renderApp('/login')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('makes only one refresh request when several callers ask at once', async () => {
    const fetchMock = mockApi({ 'POST /auth/refresh': () => json(200, authResponse()) })

    const results = await Promise.all([refreshSession(), refreshSession(), refreshSession()])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(results.every((result) => result?.user.email === demoUser.email)).toBe(true)
  })
})

describe('login', () => {
  it('logs in and returns to the page the user originally asked for', async () => {
    const fetchMock = mockApi({
      'POST /auth/refresh': noSession,
      'POST /auth/login': () => json(200, authResponse()),
      'GET /health': health,
    })
    const { router } = renderApp('/?tab=labs')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'asha@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: 'Your documents' })).toBeInTheDocument()
    expect(router.state.location.pathname + router.state.location.search).toBe('/?tab=labs')
    const loginCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/login'))
    expect(JSON.parse(String(loginCall?.[1]?.body))).toEqual({
      email: 'asha@example.com',
      password: 'correct-horse-battery',
    })
  })

  it('shows the server message for wrong credentials', async () => {
    mockApi({
      'POST /auth/refresh': noSession,
      'POST /auth/login': () =>
        json(401, { status: 401, code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect' }),
    })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'asha@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect')
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
  })

  it('shows a friendly message when the backend is unreachable', async () => {
    mockApi({ 'POST /auth/refresh': noSession })
    renderApp('/login')
    const user = userEvent.setup()
    await screen.findByLabelText('Email')
    mockApi({}).mockRejectedValue(new TypeError('Failed to fetch'))

    await user.type(screen.getByLabelText('Email'), 'asha@example.com')
    await user.type(screen.getByLabelText('Password'), 'whatever-pass')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("Can't reach Medfold right now")
  })
})

describe('register', () => {
  it('shows field errors returned by the server', async () => {
    mockApi({
      'POST /auth/refresh': noSession,
      'POST /auth/register': () =>
        json(400, {
          status: 400,
          code: 'VALIDATION_ERROR',
          message: 'Some fields are invalid',
          fields: { email: 'Enter a valid email address', password: 'Password must be 8 to 72 characters' },
        }),
    })
    renderApp('/register')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'bad')
    await user.type(screen.getByLabelText('Password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByText('Password must be 8 to 72 characters')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
  })

  it('creates the account and opens the app', async () => {
    mockApi({
      'POST /auth/refresh': noSession,
      'POST /auth/register': () => json(201, authResponse({ ...demoUser, fullName: 'New Person' })),
      'GET /health': health,
    })
    renderApp('/register')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Full name (optional)'), 'New Person')
    await user.type(screen.getByLabelText('Email'), 'asha@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Your documents' })).toBeInTheDocument()
  })
})

describe('logout', () => {
  it('ends the session and returns to login', async () => {
    const fetchMock = mockApi({
      'POST /auth/refresh': () => json(200, authResponse()),
      'POST /auth/logout': () => new Response(null, { status: 204 }),
      'GET /health': health,
    })
    const { router } = renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Log in to Medfold' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(getAccessToken()).toBeNull()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/logout'))).toBe(true)
  })
})

describe('expired access token', () => {
  const invalidToken = () =>
    json(401, { status: 401, code: 'INVALID_TOKEN', message: 'Access token is invalid or expired' })

  it('refreshes once for many parallel requests and keeps the user logged in', async () => {
    let refreshCount = 0
    const fetchMock = mockApi({
      'POST /auth/refresh': () => {
        refreshCount += 1
        // Page load gets token 1; the recovery refresh gets token 2.
        return json(200, { ...authResponse(), accessToken: `access-token-${refreshCount}` })
      },
      'GET /auth/me': (init) =>
        new Headers(init?.headers).get('Authorization') === 'Bearer access-token-2'
          ? json(200, demoUser)
          : invalidToken(),
      'GET /health': health,
    })
    renderApp('/')
    await screen.findByRole('heading', { name: 'Your documents' })

    const results = await act(() => Promise.all([authApi.me(), authApi.me(), authApi.me()]))

    expect(results.map((user) => user.email)).toEqual([demoUser.email, demoUser.email, demoUser.email])
    const refreshCalls = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))
    expect(refreshCalls).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Your documents' })).toBeInTheDocument()
  })

  it('returns to login with a message when the session has ended', async () => {
    let refreshCount = 0
    mockApi({
      'POST /auth/refresh': () => (++refreshCount === 1 ? json(200, authResponse()) : noSession()),
      'GET /auth/me': invalidToken,
      'GET /health': health,
    })
    const { router } = renderApp('/')
    await screen.findByRole('heading', { name: 'Your documents' })

    await act(() => authApi.me().catch(() => undefined))

    expect(await screen.findByText('Your session has expired. Please log in again.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(getAccessToken()).toBeNull()
  })
})
