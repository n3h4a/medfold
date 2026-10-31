import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { documentFixture, labResult, medication, page, testSummary } from '../../test/fixtures.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'
import { latestMedicines, outsideRange } from './insights.ts'

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

describe('overview', () => {
  it('welcomes a new user with the steps and a way to upload', async () => {
    mockApi(loggedIn)
    const { router } = renderApp('/')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: /, Asha$/ })).toBeInTheDocument()
    await user.click(await screen.findByRole('link', { name: /Upload your first document/ }))

    expect(router.state.location.pathname).toBe('/documents')
  })

  it('summarises the record and lists tests outside the printed range with their trend', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents': () =>
        json(200, page([documentFixture(), documentFixture({ id: 'doc-2', title: 'Metabolic Panel', status: 'PROCESSING' })])),
      'GET /labs': () =>
        json(200, [
          testSummary(),
          testSummary({ testKey: 'ldl', testName: 'LDL Cholesterol', latestFlag: 'NORMAL', resultCount: 3 }),
        ]),
      'GET /labs/hba1c': () =>
        json(200, {
          testKey: 'hba1c',
          testName: 'HbA1c',
          results: [labResult(), labResult({ id: 'lab-2', valueText: '6.1', valueNumeric: 6.1, observedOn: '2026-06-01' })],
        }),
      'GET /medications': () =>
        json(200, [
          medication(),
          medication({ id: 'med-2', name: 'Atorvastatin', prescribedOn: '2025-01-10' }),
        ]),
    })
    renderApp('/')

    const summary = await screen.findByRole('region', { name: 'Summary' })
    expect(within(summary).getByText('Documents').closest('div')?.parentElement).toHaveTextContent('2')
    expect(summary).toHaveTextContent('1 being read now')
    expect(summary).toHaveTextContent('5 results over time')

    const flagged = await screen.findByRole('link', { name: 'HbA1c' })
    expect(flagged).toHaveAttribute('href', '/labs/hba1c')
    expect(flagged.closest('li')).toHaveTextContent('Above range')
    expect(screen.queryByRole('link', { name: 'LDL Cholesterol' })).not.toBeInTheDocument()

    // Only the most recent prescription's medicines.
    expect(screen.getByText('Metformin')).toBeInTheDocument()
    expect(screen.queryByText('Atorvastatin')).not.toBeInTheDocument()
  })

  it('says so when every latest result is within range', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents': () => json(200, page([documentFixture()])),
      'GET /labs': () => json(200, [testSummary({ latestFlag: 'NORMAL' })]),
    })
    renderApp('/')

    expect(await screen.findByText('Every latest result is within the range printed on its report.')).toBeInTheDocument()
  })
})

describe('insights', () => {
  it('picks the medicines of the latest prescription date, or the first few when none are dated', () => {
    const meds = [
      medication({ id: 'a', prescribedOn: '2026-01-01' }),
      medication({ id: 'b', prescribedOn: '2026-05-01' }),
      medication({ id: 'c', prescribedOn: '2026-05-01' }),
    ]
    expect(latestMedicines(meds)).toEqual({ date: '2026-05-01', items: [meds[1], meds[2]] })
    expect(latestMedicines([medication({ prescribedOn: null })]).date).toBeNull()
  })

  it('keeps tests whose latest result is high or low', () => {
    const tests = [testSummary(), testSummary({ testKey: 'x', latestFlag: 'LOW' }), testSummary({ testKey: 'y', latestFlag: null })]
    expect(outsideRange(tests).map((t) => t.testKey)).toEqual(['hba1c', 'x'])
  })
})
