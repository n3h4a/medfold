import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { recordsApi } from '../../api/records.ts'
import { errorMessage } from '../../auth/errors.ts'
import { formatDate } from '../documents/format.ts'

/** Every medicine found in the user's documents, most recently prescribed first. */
export function MedicationsPage() {
  const { data, isPending, isError, error } = useQuery({ queryKey: ['medications'], queryFn: recordsApi.medications })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Medicines</h1>
        <p className="mt-1 text-slate-600">
          Medicines as written on your prescriptions and summaries. Follow your doctor's current instructions.
        </p>
      </div>
      {isPending && <p className="text-sm text-slate-500" role="status">Loading your medicines…</p>}
      {isError && (
        <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {errorMessage(error)}
        </p>
      )}
      {data && data.length === 0 && (
        <div className="rounded-xl border border-slate-200 bg-white px-6 py-10 text-center">
          <p className="font-medium text-slate-900">No medicines yet</p>
          <p className="mt-1 text-sm text-slate-500">They appear here once a prescription has been processed.</p>
        </div>
      )}
      {data && data.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {data.map((m) => {
            const details = [m.dosage, m.frequency, m.route, m.duration].filter(Boolean).join(' · ')
            return (
              <li key={m.id} className="flex flex-wrap items-start gap-x-4 gap-y-1 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">{m.name}</p>
                  {details && <p className="text-sm text-slate-600">{details}</p>}
                  {m.instructions && <p className="text-sm text-slate-500">{m.instructions}</p>}
                </div>
                <div className="text-right text-sm">
                  <p className="text-slate-600">{m.prescribedOn ? formatDate(m.prescribedOn) : 'No date'}</p>
                  <Link to={`/documents/${m.documentId}`} className="text-brand-600 hover:underline">
                    Source document
                  </Link>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
