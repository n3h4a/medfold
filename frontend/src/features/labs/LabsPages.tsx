import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import type { LabResult } from '../../api/documents.ts'
import { recordsApi } from '../../api/records.ts'
import { retryUnlessClientError } from '../../api/retry.ts'
import { errorMessage } from '../../auth/errors.ts'
import { FlaskIcon } from '../../components/icons.tsx'
import { EmptyState, FlagBadge, PageHeader, Problem, SkeletonList } from '../../components/ui.tsx'
import { formatDate } from '../documents/format.ts'
import { LabTrendChart } from './LabTrendChart.tsx'

/** Newest dated result first, undated ones at the end. */
function newestFirst(results: LabResult[]): LabResult[] {
  return [...results.filter((r) => r.observedOn).reverse(), ...results.filter((r) => !r.observedOn)]
}

/** Every test the user has results for, each linking to its trend. `?filter=outside` shows only those outside range. */
export function LabsPage() {
  const { data, isPending, isError, error } = useQuery({ queryKey: ['labs'], queryFn: recordsApi.labTests })
  const [params, setParams] = useSearchParams()
  const [name, setName] = useState('')
  const outsideOnly = params.get('filter') === 'outside'

  const needle = name.trim().toLowerCase()
  const shown = (data ?? []).filter(
    (t) => (!outsideOnly || t.latestFlag === 'HIGH' || t.latestFlag === 'LOW') && (!needle || t.testName.toLowerCase().includes(needle)),
  )
  const outsideCount = (data ?? []).filter((t) => t.latestFlag === 'HIGH' || t.latestFlag === 'LOW').length
  const tab = (active: boolean) =>
    `rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${active ? 'bg-surface text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`

  return (
    <div className="animate-rise space-y-6">
      <PageHeader title="Test results" subtitle="Every test found in your reports. Open one to see it over time." />
      {isPending && <SkeletonList label="Loading your test results…" />}
      {isError && <Problem>{errorMessage(error)}</Problem>}
      {data && data.length === 0 && (
        <EmptyState icon={<FlaskIcon />} title="No test results yet">
          Results appear here once a lab report has been processed.
        </EmptyState>
      )}
      {data && data.length > 0 && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <div role="group" aria-label="Show" className="inline-flex rounded-lg bg-slate-100 p-1">
              <button type="button" aria-pressed={!outsideOnly} className={tab(!outsideOnly)} onClick={() => setParams({})}>
                All tests <span className="text-slate-400">{data.length}</span>
              </button>
              <button
                type="button"
                aria-pressed={outsideOnly}
                className={tab(outsideOnly)}
                onClick={() => setParams({ filter: 'outside' })}
              >
                Outside range <span className="text-slate-400">{outsideCount}</span>
              </button>
            </div>
            <input
              type="search"
              aria-label="Filter tests by name"
              placeholder="Filter by name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-surface px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15 sm:max-w-xs"
            />
          </div>
          {shown.length === 0 ? (
            <p className="text-sm text-slate-500">No tests match.</p>
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2">
              {shown.map((test) => (
                <li
                  key={test.testKey}
                  className="relative rounded-2xl border border-slate-200 bg-surface p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-md"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <Link
                        to={`/labs/${encodeURIComponent(test.testKey)}`}
                        className="font-medium text-slate-900 after:absolute after:inset-0 hover:text-brand-ink focus:outline-none focus-visible:underline"
                      >
                        {test.testName}
                      </Link>
                      <p className="text-sm text-slate-500">
                        {test.resultCount} {test.resultCount === 1 ? 'result' : 'results'}
                        {test.latestObservedOn && ` · latest ${formatDate(test.latestObservedOn)}`}
                      </p>
                    </div>
                    <p className="shrink-0 text-right text-lg font-semibold text-slate-900">
                      {test.latestValueText}
                      {test.unit && <span className="ml-1 text-sm font-normal text-slate-500">{test.unit}</span>}
                    </p>
                  </div>
                  <div className="mt-3 flex items-center justify-between gap-3">
                    <FlagBadge flag={test.latestFlag} long />
                    {test.previousValueText && (
                      <p className="truncate text-xs text-slate-500">
                        was {test.previousValueText}
                        {test.previousObservedOn && ` on ${formatDate(test.previousObservedOn)}`}
                      </p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}

/** One test across all reports: chart plus a table of every value with a link to its source document. */
export function LabTrendPage() {
  const { testKey = '' } = useParams()
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['labs', testKey],
    queryFn: () => recordsApi.labTrend(testKey),
    retry: retryUnlessClientError,
  })

  return (
    <div className="animate-rise space-y-4">
      <Link to="/labs" className="inline-flex items-center gap-1 text-sm font-medium text-brand-ink hover:underline">
        <span aria-hidden="true">←</span> All test results
      </Link>
      {isPending && <SkeletonList rows={2} label="Loading…" />}
      {isError && (
        <Problem>
          {error instanceof ApiError && error.status === 404 ? 'No results found for this test.' : errorMessage(error)}
        </Problem>
      )}
      {data && (
        <>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{data.testName}</h1>
          <LabTrendChart testName={data.testName} results={data.results} />
          <section aria-labelledby="all-results-heading" className="rounded-2xl border border-slate-200 bg-surface p-4 shadow-sm">
            <h2 id="all-results-heading" className="text-sm font-semibold text-slate-900">
              All results
            </h2>
            <div className="-mx-4 mt-2 overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-2 font-medium">Date</th>
                    <th scope="col" className="px-4 py-2 font-medium">Result</th>
                    <th scope="col" className="px-4 py-2 font-medium">Reference range</th>
                    <th scope="col" className="px-4 py-2 font-medium">Compared with range</th>
                    <th scope="col" className="px-4 py-2 font-medium"><span className="sr-only">Source</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {newestFirst(data.results).map((r) => (
                    <tr key={r.id}>
                      <td className="whitespace-nowrap px-4 py-2 text-slate-600">
                        {r.observedOn ? formatDate(r.observedOn) : 'No date'}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2 text-slate-900">
                        {r.valueText}
                        {r.unit && <span className="ml-1 text-slate-500">{r.unit}</span>}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2 text-slate-600">{r.refText ?? '—'}</td>
                      <td className="px-4 py-2">
                        <FlagBadge flag={r.flag} long />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Link to={`/documents/${r.documentId}`} className="text-brand-ink hover:underline">
                          Report
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-3 text-xs text-slate-500">
              Each value is compared with the range printed on its own report. This is not a medical judgment.
            </p>
          </section>
        </>
      )}
    </div>
  )
}
