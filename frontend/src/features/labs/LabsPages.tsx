import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import type { LabFlag, LabResult } from '../../api/documents.ts'
import { recordsApi } from '../../api/records.ts'
import { retryUnlessClientError } from '../../api/retry.ts'
import { errorMessage } from '../../auth/errors.ts'
import { flagLabel, formatDate } from '../documents/format.ts'
import { LabTrendChart } from './LabTrendChart.tsx'

function FlagText({ flag }: { flag: LabFlag | null }) {
  return flag ? <span className="text-slate-600">{flagLabel[flag]}</span> : <span className="text-slate-400">—</span>
}

/** Newest dated result first, undated ones at the end. */
function newestFirst(results: LabResult[]): LabResult[] {
  return [...results.filter((r) => r.observedOn).reverse(), ...results.filter((r) => !r.observedOn)]
}

function Problem({ children }: { children: string }) {
  return (
    <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
      {children}
    </p>
  )
}

/** Every test the user has results for, each linking to its trend. */
export function LabsPage() {
  const { data, isPending, isError, error } = useQuery({ queryKey: ['labs'], queryFn: recordsApi.labTests })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Test results</h1>
        <p className="mt-1 text-slate-600">Every test found in your reports. Open one to see it over time.</p>
      </div>
      {isPending && <p className="text-sm text-slate-500" role="status">Loading your test results…</p>}
      {isError && <Problem>{errorMessage(error)}</Problem>}
      {data && data.length === 0 && (
        <div className="rounded-xl border border-slate-200 bg-white px-6 py-10 text-center">
          <p className="font-medium text-slate-900">No test results yet</p>
          <p className="mt-1 text-sm text-slate-500">Results appear here once a lab report has been processed.</p>
        </div>
      )}
      {data && data.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {data.map((test) => (
            <li key={test.testKey} className="relative flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 hover:bg-slate-50">
              <div className="min-w-0 flex-1">
                <Link
                  to={`/labs/${encodeURIComponent(test.testKey)}`}
                  className="font-medium text-slate-900 after:absolute after:inset-0 hover:text-brand-700 focus:outline-none focus-visible:underline"
                >
                  {test.testName}
                </Link>
                <p className="text-sm text-slate-500">
                  {test.resultCount} {test.resultCount === 1 ? 'result' : 'results'}
                  {test.latestObservedOn && ` · latest ${formatDate(test.latestObservedOn)}`}
                </p>
              </div>
              <div className="text-right text-sm">
                <p className="font-medium text-slate-900">
                  {test.latestValueText}
                  {test.unit && <span className="ml-1 font-normal text-slate-500">{test.unit}</span>}
                </p>
                <p className="text-xs">
                  <FlagText flag={test.latestFlag} />
                </p>
              </div>
            </li>
          ))}
        </ul>
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
    <div className="space-y-4">
      <Link to="/labs" className="inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline">
        <span aria-hidden="true">←</span> All test results
      </Link>
      {isPending && <p className="text-sm text-slate-500" role="status">Loading…</p>}
      {isError && (
        <Problem>
          {error instanceof ApiError && error.status === 404 ? 'No results found for this test.' : errorMessage(error)}
        </Problem>
      )}
      {data && (
        <>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{data.testName}</h1>
          <LabTrendChart testName={data.testName} results={data.results} />
          <section aria-labelledby="all-results-heading" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
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
                        <FlagText flag={r.flag} />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Link to={`/documents/${r.documentId}`} className="text-brand-600 hover:underline">
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
