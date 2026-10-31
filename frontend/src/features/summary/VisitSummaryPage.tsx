import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { documentsApi } from '../../api/documents.ts'
import { recordsApi, type LabTestSummary } from '../../api/records.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ClipboardIcon, PrinterIcon } from '../../components/icons.tsx'
import { Card, EmptyState, FlagBadge, PageHeader, Problem, SkeletonList } from '../../components/ui.tsx'
import { errorMessage } from '../../auth/errors.ts'
import { displayTitle, formatDate, typeLabel } from '../documents/format.ts'
import { latestMedicines, outsideRange } from '../overview/insights.ts'

/** Outside the printed range first, then alphabetical: what a doctor would want to see first. */
function orderForDoctor(tests: LabTestSummary[]): LabTestSummary[] {
  const rank = (t: LabTestSummary) => (t.latestFlag === 'HIGH' || t.latestFlag === 'LOW' ? 0 : 1)
  return [...tests].sort((a, b) => rank(a) - rank(b) || a.testName.localeCompare(b.testName))
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="break-inside-avoid">
      <h2 className="mb-2 border-b border-slate-200 pb-1 text-sm font-semibold uppercase tracking-wider text-slate-500">{title}</h2>
      {children}
    </section>
  )
}

/**
 * One page to hand to a doctor instead of a folder of paper: latest value of every test with its
 * previous value, the latest prescription, and the documents it comes from. Prints cleanly.
 */
export function VisitSummaryPage() {
  const { state } = useAuth()
  const labs = useQuery({ queryKey: ['labs'], queryFn: recordsApi.labTests })
  const medications = useQuery({ queryKey: ['medications'], queryFn: recordsApi.medications })
  const documents = useQuery({ queryKey: ['documents'], queryFn: () => documentsApi.list() })

  const [preparedAt] = useState(() => new Date().toISOString())
  const user = state.status === 'authenticated' ? state.user : null
  const failed = [labs, medications, documents].find((q) => q.isError)
  const loading = labs.isPending || medications.isPending || documents.isPending
  const tests = orderForDoctor(labs.data ?? [])
  const flagged = outsideRange(tests).length
  const { date: prescribedOn, items: medicines } = latestMedicines(medications.data ?? [])
  const docs = (documents.data?.items ?? []).filter((d) => d.status === 'READY')

  return (
    <div className="animate-rise space-y-6">
      <div className="no-print">
        <PageHeader
          title="Visit summary"
          subtitle="Everything on one page for your next appointment. Print it or save it as a PDF."
          actions={
            <button
              type="button"
              onClick={() => window.print()}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-brand-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 disabled:opacity-60"
            >
              <PrinterIcon className="h-4 w-4" /> Print or save as PDF
            </button>
          }
        />
      </div>

      {failed && <Problem>{errorMessage(failed.error)}</Problem>}
      {loading && !failed && <SkeletonList rows={3} label="Preparing your summary" />}
      {!loading && !failed && docs.length === 0 && (
        <EmptyState icon={<ClipboardIcon />} title="Nothing to summarise yet">
          Upload a report or prescription and it will appear here.
        </EmptyState>
      )}

      {!loading && !failed && docs.length > 0 && (
        <Card className="print-plain space-y-8 p-6 sm:p-10">
          <header className="flex flex-wrap items-start justify-between gap-4 border-b-2 border-brand-600 pb-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-widest text-brand-ink">Medfold · Patient-held record</p>
              <p className="mt-1 text-2xl font-semibold text-slate-900">{user?.fullName ?? user?.email}</p>
              {user?.fullName && <p className="text-sm text-slate-500">{user.email}</p>}
            </div>
            <div className="text-right text-sm text-slate-600">
              <p>Prepared {formatDate(preparedAt)}</p>
              <p>
                {docs.length} documents · {tests.length} tests · {flagged} outside printed range
              </p>
            </div>
          </header>

          <Section title="Latest test results">
            {tests.length === 0 ? (
              <p className="text-sm text-slate-500">No test results found.</p>
            ) : (
              <div className="-mx-2 overflow-x-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="text-xs text-slate-500">
                    <tr>
                      <th scope="col" className="px-2 py-1.5 font-medium">Test</th>
                      <th scope="col" className="px-2 py-1.5 font-medium">Latest</th>
                      <th scope="col" className="px-2 py-1.5 font-medium">Printed range</th>
                      <th scope="col" className="px-2 py-1.5 font-medium">Compared</th>
                      <th scope="col" className="px-2 py-1.5 font-medium">Previous</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {tests.map((t) => (
                      <tr key={t.testKey}>
                        <td className="px-2 py-2 font-medium text-slate-900">{t.testName}</td>
                        <td className="whitespace-nowrap px-2 py-2 text-slate-900">
                          <span className="font-semibold">{t.latestValueText}</span>
                          {t.unit && <span className="ml-1 text-slate-500">{t.unit}</span>}
                          {t.latestObservedOn && <span className="block text-xs text-slate-500">{formatDate(t.latestObservedOn)}</span>}
                        </td>
                        <td className="whitespace-nowrap px-2 py-2 text-slate-600">{t.latestRefText ?? '—'}</td>
                        <td className="px-2 py-2">
                          <FlagBadge flag={t.latestFlag} />
                        </td>
                        <td className="whitespace-nowrap px-2 py-2 text-slate-600">
                          {t.previousValueText ?? '—'}
                          {t.previousObservedOn && <span className="block text-xs text-slate-500">{formatDate(t.previousObservedOn)}</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Section>

          <Section title={`Latest prescription${prescribedOn ? ` · ${formatDate(prescribedOn)}` : ''}`}>
            {medicines.length === 0 ? (
              <p className="text-sm text-slate-500">No medicines found.</p>
            ) : (
              <ul className="grid gap-x-8 gap-y-2 sm:grid-cols-2">
                {medicines.map((m) => (
                  <li key={m.id} className="text-sm">
                    <span className="font-medium text-slate-900">{m.name}</span>
                    <span className="text-slate-600">
                      {' '}
                      {[m.dosage, m.frequency, m.duration].filter(Boolean).join(' · ')}
                    </span>
                    {m.instructions && <span className="block text-xs text-slate-500">{m.instructions}</span>}
                  </li>
                ))}
              </ul>
            )}
          </Section>

          <Section title="Source documents">
            <ul className="grid gap-x-8 gap-y-1 text-sm sm:grid-cols-2">
              {docs.map((d) => (
                <li key={d.id} className="flex justify-between gap-3 text-slate-700">
                  <span className="truncate">{displayTitle(d)}</span>
                  <span className="shrink-0 text-slate-500">
                    {[d.docType ? typeLabel[d.docType] : null, d.docDate ? formatDate(d.docDate) : null].filter(Boolean).join(' · ')}
                  </span>
                </li>
              ))}
            </ul>
          </Section>

          <p className="border-t border-slate-200 pt-4 text-xs text-slate-500">
            Values are copied from the patient's own reports. “Compared” only says whether a value sits inside the range printed
            on that same report; it is not a diagnosis. Please refer to the original documents for clinical decisions.
          </p>
        </Card>
      )}
    </div>
  )
}
