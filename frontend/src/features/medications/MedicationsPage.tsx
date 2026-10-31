import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { recordsApi } from '../../api/records.ts'
import { errorMessage } from '../../auth/errors.ts'
import { PillIcon } from '../../components/icons.tsx'
import { EmptyState, PageHeader, Problem, SkeletonList } from '../../components/ui.tsx'
import { formatDate } from '../documents/format.ts'

/** Every medicine found in the user's documents, most recently prescribed first. */
export function MedicationsPage() {
  const { data, isPending, isError, error } = useQuery({ queryKey: ['medications'], queryFn: recordsApi.medications })

  return (
    <div className="animate-rise space-y-6">
      <PageHeader
        title="Medicines"
        subtitle="Medicines as written on your prescriptions and summaries. Follow your doctor's current instructions."
      />
      {isPending && <SkeletonList label="Loading your medicines…" />}
      {isError && <Problem>{errorMessage(error)}</Problem>}
      {data && data.length === 0 && (
        <EmptyState icon={<PillIcon />} title="No medicines yet">
          They appear here once a prescription has been processed.
        </EmptyState>
      )}
      {data && data.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-surface shadow-sm">
          {data.map((m) => {
            const details = [m.dosage, m.frequency, m.route, m.duration].filter(Boolean).join(' · ')
            return (
              <li key={m.id} className="flex flex-wrap items-start gap-x-4 gap-y-1 px-4 py-4">
                <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-50 text-violet-700">
                  <PillIcon />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">{m.name}</p>
                  {details && <p className="text-sm text-slate-600">{details}</p>}
                  {m.instructions && <p className="text-sm text-slate-500">{m.instructions}</p>}
                </div>
                <div className="text-right text-sm">
                  <p className="text-slate-600">{m.prescribedOn ? formatDate(m.prescribedOn) : 'No date'}</p>
                  <Link to={`/documents/${m.documentId}`} className="text-brand-ink hover:underline">
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
