import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { LabResult, Medication } from '../../api/documents.ts'
import type { LabTestSummary } from '../../api/records.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'
import { formatDate } from '../documents/format.ts'
import { plottablePoints } from './trendPoints.ts'

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

function lab(overrides: Partial<LabResult>): LabResult {
  return {
    id: 'lab-1', documentId: 'doc-1', testName: 'HbA1c', testKey: 'hba1c', valueText: '5.4', valueNumeric: 5.4,
    unit: '%', refText: '4.0 - 5.6', refLow: 4, refHigh: 5.6, flag: 'NORMAL', observedOn: '2025-06-01', pageNumber: 1,
    userEdited: false, ...overrides,
  }
}

const hba1cTrend = {
  testKey: 'hba1c',
  testName: 'HbA1c',
  // As the API returns them: oldest first, undated last.
  results: [
    lab({ id: 'lab-3', documentId: 'doc-3', valueText: '42', valueNumeric: 42, unit: 'mmol/mol', observedOn: '2024-01-01' }),
    lab({}),
    lab({ id: 'lab-2', documentId: 'doc-2', valueText: '6.1', valueNumeric: 6.1, flag: 'HIGH', observedOn: '2026-06-01' }),
    lab({ id: 'lab-4', documentId: 'doc-4', valueText: '5.9', valueNumeric: 5.9, observedOn: null }),
  ],
}

const summaries: LabTestSummary[] = [
  {
    testKey: 'fasting_glucose', testName: 'Fasting glucose', unit: 'mg/dL', resultCount: 1, latestValueText: '92',
    latestFlag: 'NORMAL', latestObservedOn: '2025-06-01', latestDocumentId: 'doc-1',
  },
  {
    testKey: 'hba1c', testName: 'HbA1c', unit: '%', resultCount: 4, latestValueText: '6.1', latestFlag: 'HIGH',
    latestObservedOn: '2026-06-01', latestDocumentId: 'doc-2',
  },
]

describe('test results', () => {
  it('lists every test with its latest value and opens its trend', async () => {
    mockApi({
      ...loggedIn,
      'GET /labs': () => json(200, summaries),
      'GET /labs/hba1c': () => json(200, hba1cTrend),
    })
    const { router } = renderApp('/labs')
    const user = userEvent.setup()

    const link = await screen.findByRole('link', { name: 'HbA1c' })
    const row = link.closest('li') as HTMLElement
    expect(row).toHaveTextContent(`4 results · latest ${formatDate('2026-06-01')}`)
    expect(row).toHaveTextContent('6.1%')
    expect(within(row).getByText('Above printed range')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Test results' })).toHaveAttribute('aria-current', 'page')

    await user.click(link)

    expect(router.state.location.pathname).toBe('/labs/hba1c')
    expect(await screen.findByRole('heading', { name: 'HbA1c' })).toBeInTheDocument()
  })

  it('shows a trend chart of comparable values and a table of every value with its source', async () => {
    mockApi({ ...loggedIn, 'GET /labs/hba1c': () => json(200, hba1cTrend) })

    renderApp('/labs/hba1c')

    const chart = await screen.findByRole('img', {
      name: new RegExp(`HbA1c: 3 values from ${formatDate('2024-01-01')} to ${formatDate('2026-06-01')}`),
    })
    expect(chart).toBeInTheDocument()
    expect(screen.getByLabelText(`${formatDate('2026-06-01')}: 6.1 %`)).toBeInTheDocument()
    expect(screen.getByText(/reference range printed on the latest report \(4.0 - 5.6\)/)).toBeInTheDocument()
    expect(screen.getByLabelText(`${formatDate('2024-01-01')}: 5.99 % (printed as 42 mmol/mol)`)).toBeInTheDocument()
    expect(screen.getByText(/1 value\(s\) printed in another unit are converted to % for the chart/)).toBeInTheDocument()
    expect(screen.getByText(/1 result\(s\) without a date, a number or a convertible unit/)).toBeInTheDocument()

    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(4)
    expect(rows[0]).toHaveTextContent(formatDate('2026-06-01'))
    expect(rows[0]).toHaveTextContent('Above printed range')
    expect(within(rows[0]).getByRole('link', { name: 'Report' })).toHaveAttribute('href', '/documents/doc-2')
    expect(rows[2]).toHaveTextContent('42mmol/mol')
    expect(rows[3]).toHaveTextContent('No date')
  })

  it('says so when there are no results for a test', async () => {
    mockApi({
      ...loggedIn,
      'GET /labs/ldl': () => json(404, { status: 404, code: 'LAB_TEST_NOT_FOUND', message: 'No results found for this test' }),
    })

    renderApp('/labs/ldl')

    expect(await screen.findByRole('alert')).toHaveTextContent('No results found for this test.')
  })

  it('explains an empty list', async () => {
    mockApi({ ...loggedIn, 'GET /labs': () => json(200, []) })

    renderApp('/labs')

    expect(await screen.findByText('No test results yet')).toBeInTheDocument()
  })
})

describe('trend points', () => {
  it('keeps dated numeric values in the latest unit, converting standard units, oldest first', () => {
    const { points, unit, skipped, converted } = plottablePoints(hba1cTrend.results)

    expect(unit).toBe('%')
    expect(points.map((p) => Number(p.value.toFixed(2)))).toEqual([5.99, 5.4, 6.1])
    expect(points.map((p) => p.converted)).toEqual([true, false, false])
    expect(converted).toBe(1)
    expect(skipped).toBe(1)
  })

  it('leaves out values in a unit it has no standard conversion for', () => {
    const { points, skipped } = plottablePoints([
      lab({ id: 'a', testKey: 'tsh', unit: 'uIU/mL', valueNumeric: 2.1, observedOn: '2025-01-01' }),
      lab({ id: 'b', testKey: 'tsh', unit: 'mIU/L', valueNumeric: 2.4, observedOn: '2026-01-01' }),
    ])
    expect(points.map((p) => p.value)).toEqual([2.4])
    expect(skipped).toBe(1)
  })

  it('has nothing to plot without numbers', () => {
    expect(plottablePoints([lab({ valueText: 'Negative', valueNumeric: null })]).points).toEqual([])
  })
})

describe('medicines', () => {
  it('lists medicines with their details and source document', async () => {
    const medicines: Medication[] = [
      {
        id: 'med-1', documentId: 'doc-7', name: 'Metformin 500 mg', dosage: '500 mg', frequency: '1-0-1', route: 'oral',
        duration: '30 days', instructions: 'after food', prescribedOn: '2026-02-01', userEdited: false,
      },
    ]
    mockApi({ ...loggedIn, 'GET /medications': () => json(200, medicines) })

    renderApp('/medications')

    expect(await screen.findByText('Metformin 500 mg')).toBeInTheDocument()
    expect(screen.getByText('500 mg · 1-0-1 · oral · 30 days')).toBeInTheDocument()
    expect(screen.getByText('after food')).toBeInTheDocument()
    expect(screen.getByText(formatDate('2026-02-01'))).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Source document' })).toHaveAttribute('href', '/documents/doc-7')
  })
})
