import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { documentFixture, testSummary } from '../../test/fixtures.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

describe('search palette', () => {
  it('opens with Ctrl+K, finds tests and documents, and opens the chosen one with the keyboard', async () => {
    mockApi({
      ...loggedIn,
      'GET /labs': () => json(200, [testSummary({ testName: 'Haemoglobin', testKey: 'haemoglobin' })]),
      'GET /search': () => json(200, [{ document: documentFixture(), snippet: '…Haemoglobin 11.4 g/dL…', pageNumber: 1 }]),
    })
    const { router } = renderApp('/')
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: /, Asha$/ })

    await user.keyboard('{Control>}k{/Control}')
    const box = await screen.findByRole('combobox', { name: 'Search pages, tests and documents' })
    await user.type(box, 'haemo')

    expect(await screen.findByRole('option', { name: /Complete Blood Count/ })).toBeInTheDocument()
    const test = screen.getByRole('option', { name: /^Haemoglobin/ })
    expect(test).toHaveAttribute('aria-selected', 'true')

    await user.keyboard('{ArrowDown}{Enter}')

    expect(router.state.location.pathname).toBe('/documents/doc-1')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('lists pages before typing and closes with Escape', async () => {
    mockApi(loggedIn)
    const { router } = renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Search' }))
    expect(screen.getByRole('option', { name: /Visit summary/ })).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Search\s*⌘K/ }))
    await user.click(screen.getByRole('option', { name: /Medicines/ }))
    expect(router.state.location.pathname).toBe('/medications')
  })

  it('says when nothing matches', async () => {
    mockApi({ ...loggedIn, 'GET /search': () => json(200, []) })
    renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Search' }))
    await user.type(screen.getByRole('combobox'), 'zzz')

    expect(await screen.findByText('Nothing found for “zzz”')).toBeInTheDocument()
  })
})

describe('theme', () => {
  it('switches to dark mode and remembers the choice', async () => {
    mockApi(loggedIn)
    renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Switch to dark mode' }))

    expect(document.documentElement).toHaveClass('dark')
    expect(localStorage.getItem('medfold-theme')).toBe('dark')
    await user.click(screen.getByRole('button', { name: 'Switch to light mode' }))
    expect(document.documentElement).not.toHaveClass('dark')
  })
})
