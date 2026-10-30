import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { ReactNode } from 'react'
import { documentsApi, type LabFlag, type LabResult, type MedicalDocument, type Medication } from '../../api/documents.ts'
import { retryUnlessClientError } from '../../api/retry.ts'
import { errorMessage } from '../../auth/errors.ts'
import { flagLabel } from './format.ts'

const flagClasses: Record<LabFlag, string> = {
  LOW: 'bg-sky-50 text-sky-800 ring-sky-200',
  NORMAL: 'bg-slate-50 text-slate-700 ring-slate-200',
  HIGH: 'bg-amber-50 text-amber-800 ring-amber-200',
}

function FlagBadge({ flag }: { flag: LabFlag | null }) {
  if (!flag) return <span className="text-xs text-slate-400">—</span>
  return (
    <span className={`whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${flagClasses[flag]}`}>
      {flagLabel[flag]}
    </span>
  )
}

function Card({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 id={id} className="text-sm font-semibold text-slate-900">
        {title}
      </h2>
      {children}
    </section>
  )
}

function LabTable({ results }: { results: LabResult[] }) {
  return (
    <Card id="labs-heading" title="Test results">
      <div className="-mx-4 mt-2 overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-4 py-2 font-medium">Test</th>
              <th scope="col" className="px-4 py-2 font-medium">Result</th>
              <th scope="col" className="px-4 py-2 font-medium">Reference range</th>
              <th scope="col" className="px-4 py-2 font-medium">Compared with range</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {results.map((r) => (
              <tr key={r.id}>
                <th scope="row" className="px-4 py-2 font-medium text-slate-900">
                  <Link to={`/labs/${encodeURIComponent(r.testKey)}`} className="hover:text-brand-700 hover:underline">
                    {r.testName}
                  </Link>
                </th>
                <td className="whitespace-nowrap px-4 py-2 text-slate-900">
                  {r.valueText}
                  {r.unit && <span className="ml-1 text-slate-500">{r.unit}</span>}
                </td>
                <td className="whitespace-nowrap px-4 py-2 text-slate-600">{r.refText ?? '—'}</td>
                <td className="px-4 py-2">
                  <FlagBadge flag={r.flag} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-slate-500">
        Values are copied from the report. The comparison uses the reference range printed on the same report and is
        not a medical judgment. Check the original if something looks wrong.
      </p>
    </Card>
  )
}

function MedicationList({ medications }: { medications: Medication[] }) {
  return (
    <Card id="medications-heading" title="Medicines">
      <ul className="mt-2 divide-y divide-slate-100">
        {medications.map((m) => {
          const details = [m.dosage, m.frequency, m.route, m.duration].filter(Boolean).join(' · ')
          return (
            <li key={m.id} className="py-2">
              <p className="text-sm font-medium text-slate-900">{m.name}</p>
              {details && <p className="text-sm text-slate-600">{details}</p>}
              {m.instructions && <p className="text-sm text-slate-500">{m.instructions}</p>}
            </li>
          )
        })}
      </ul>
      <p className="mt-3 text-xs text-slate-500">As written on the document. Follow your doctor's instructions.</p>
    </Card>
  )
}

/** Lab results, medicines and explained terms extracted from a processed document. */
export function ExtractedResults({ document }: { document: MedicalDocument }) {
  const { data, isPending, isError, error } = useQuery({
    // Status is part of the key so results are fetched again after reprocessing finishes.
    queryKey: ['documents', document.id, 'results', document.status, document.updatedAt],
    queryFn: () => documentsApi.results(document.id),
    enabled: document.status === 'READY',
    retry: retryUnlessClientError,
  })

  if (document.status !== 'READY') return null
  if (isPending) {
    return <p className="text-sm text-slate-500" role="status">Loading extracted results…</p>
  }
  if (isError) {
    return (
      <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
        {errorMessage(error)}
      </p>
    )
  }
  if (data.labResults.length === 0 && data.medications.length === 0 && data.terms.length === 0) {
    return null
  }

  return (
    <div className="space-y-4">
      {data.labResults.length > 0 && <LabTable results={data.labResults} />}
      {data.medications.length > 0 && <MedicationList medications={data.medications} />}
      {data.terms.length > 0 && (
        <Card id="terms-heading" title="Terms in this document">
          <dl className="mt-2 space-y-2">
            {data.terms.map((t) => (
              <div key={t.term}>
                <dt className="text-sm font-medium text-slate-900">{t.term}</dt>
                <dd className="text-sm text-slate-600">{t.definition}</dd>
              </div>
            ))}
          </dl>
        </Card>
      )}
    </div>
  )
}
