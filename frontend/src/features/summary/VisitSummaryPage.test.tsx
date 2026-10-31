import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { documentFixture, medication, page, testSummary } from '../../test/fixtures.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

describe('visit summary', () => {
  it('puts results outside the printed range first, with the previous value, medicines and sources', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents': () =>
        json(200, page([documentFixture(), documentFixture({ id: 'doc-9', title: 'Still reading', status: 'PROCESSING' })])),
      'GET /labs': () =>
        json(200, [
          testSummary({ testKey: 'esr', testName: 'ESR', latestFlag: 'NORMAL', previousValueText: null, previousObservedOn: null }),
          testSummary(),
        ]),
      'GET /medications': () => json(200, [medication()]),
    })
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    renderApp('/summary')
    const user = userEvent.setup()

    const table = await screen.findByRole('table')
    expect(screen.getByText('Medfold · Patient-held record').parentElement).toHaveTextContent('Asha Rao')
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows[0]).toHaveTextContent('HbA1c')
    expect(rows[0]).toHaveTextContent('4.0 - 5.6')
    expect(rows[0]).toHaveTextContent('5.4')
    expect(rows[1]).toHaveTextContent('ESR')
    expect(screen.getByText('Metformin')).toBeInTheDocument()
    expect(screen.getByText('Complete Blood Count')).toBeInTheDocument()
    expect(screen.queryByText('Still reading')).not.toBeInTheDocument()
    expect(screen.getByText(/1 documents · 2 tests · 1 outside printed range/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Print or save as PDF' }))
    expect(print).toHaveBeenCalled()
  })

  it('explains that there is nothing to summarise yet', async () => {
    mockApi(loggedIn)
    renderApp('/summary')

    expect(await screen.findByText('Nothing to summarise yet')).toBeInTheDocument()
  })
})
