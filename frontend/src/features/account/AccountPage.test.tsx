import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

describe('account page', () => {
  it('is reached from the header and shows who is logged in', async () => {
    mockApi(loggedIn)
    const { router } = renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Your account (asha@example.com)' }))

    expect(router.state.location.pathname).toBe('/account')
    expect(await screen.findByText('Asha Rao · asha@example.com')).toBeInTheDocument()
  })

  it('deletes the account with the password and returns to login', async () => {
    let sent: unknown = null
    mockApi({
      ...loggedIn,
      'DELETE /account': (init) => {
        sent = JSON.parse(String(init?.body))
        return new Response(null, { status: 204 })
      },
      'POST /auth/logout': () => new Response(null, { status: 204 }),
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { router } = renderApp('/account')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Your password'), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Delete my account' }))

    expect(await screen.findByRole('heading', { name: /log in/i })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(sent).toEqual({ password: 'secret-password' })
  })

  it('keeps the account when the password is wrong or the user cancels', async () => {
    const remove = vi.fn(() => json(403, { status: 403, code: 'WRONG_PASSWORD', message: 'Password is incorrect' }))
    mockApi({ ...loggedIn, 'DELETE /account': remove })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    renderApp('/account')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Your password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Delete my account' }))
    expect(remove).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Delete my account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Password is incorrect')
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('heading', { name: 'Your account' })).toBeInTheDocument()
  })
})
