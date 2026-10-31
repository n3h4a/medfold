import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { documentsApi, type DocumentType, type MedicalDocument } from '../../api/documents.ts'
import { errorMessage } from '../../auth/errors.ts'
import { displayTitle, formatBytes, formatDate, isInProgress, polling, timelineGroup, typeLabel } from './format.ts'
import { DocumentTypeIcon } from './DocumentTypeIcon.tsx'
import { StatusPill } from './StatusPill.tsx'
import { EmptyState, Problem, SkeletonList } from '../../components/ui.tsx'
import { FolderIcon } from '../../components/icons.tsx'

function DocumentRow({ document }: { document: MedicalDocument }) {
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => documentsApi.remove(document.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  })
  const title = displayTitle(document)
  const meta = [
    document.docType ? typeLabel[document.docType] : null,
    document.docDate ? formatDate(document.docDate) : `Uploaded ${formatDate(document.createdAt)}`,
    formatBytes(document.sizeBytes),
  ].filter(Boolean)

  return (
    <li className="relative flex items-center gap-3 px-4 py-3 transition-colors hover:bg-slate-50">
      <DocumentTypeIcon type={document.docType} mimeType={document.mimeType} />
      <div className="min-w-0 flex-1">
        <Link
          to={`/documents/${document.id}`}
          className="block truncate font-medium text-slate-900 after:absolute after:inset-0 hover:text-brand-ink focus:outline-none focus-visible:underline"
        >
          {title}
        </Link>
        <p className="truncate text-sm text-slate-500">{meta.join(' · ')}</p>
        {remove.isError && <p className="text-sm text-red-700">{errorMessage(remove.error)}</p>}
      </div>
      <StatusPill status={document.status} className="hidden sm:inline" />
      <button
        type="button"
        disabled={remove.isPending}
        onClick={() => {
          if (window.confirm(`Delete "${title}"? This cannot be undone.`)) remove.mutate()
        }}
        aria-label={`Delete ${title}`}
        className="relative z-10 shrink-0 rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-400 disabled:opacity-50"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.5">
          <path strokeLinecap="round" strokeLinejoin="round" d="M4 7h16M10 11v6m4-6v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3" />
        </svg>
      </button>
    </li>
  )
}

function groupByMonth(documents: MedicalDocument[]): [string, MedicalDocument[]][] {
  const groups = new Map<string, MedicalDocument[]>()
  for (const document of documents) {
    const key = timelineGroup(document)
    groups.set(key, [...(groups.get(key) ?? []), document])
  }
  return [...groups.entries()]
}

/** Every document in timeline order, optionally only one kind. */
export function DocumentList({ type = null }: { type?: DocumentType | null }) {
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['documents'],
    queryFn: () => documentsApi.list(),
    // Keep statuses current while any document is still being processed.
    refetchInterval: (query) =>
      query.state.data?.items.some((d) => isInProgress(d.status)) ? polling.intervalMs : false,
  })

  if (isPending) {
    return <SkeletonList label="Loading your documents…" />
  }
  if (isError) {
    return <Problem>{errorMessage(error)}</Problem>
  }
  if (data.items.length === 0) {
    return (
      <EmptyState icon={<FolderIcon />} title="No documents yet">
        Upload a lab report, prescription or discharge summary to get started.
      </EmptyState>
    )
  }
  const items = type ? data.items.filter((d) => d.docType === type) : data.items

  return (
    <section aria-labelledby="documents-heading">
      <h2 id="documents-heading" className="mb-2 text-sm font-semibold text-slate-900">
        {type ? `${items.length} of ${data.totalItems}` : data.totalItems} {data.totalItems === 1 ? 'document' : 'documents'}
      </h2>
      {/* The API returns timeline order (record date, newest first; undated last), so groups come out in order. */}
      <div className="relative space-y-6 border-l-2 border-slate-200 pl-6">
        {groupByMonth(items).map(([month, documents]) => (
          <div key={month} className="relative">
            <span aria-hidden="true" className="absolute -left-[33px] top-0.5 h-4 w-4 rounded-full border-4 border-slate-50 bg-brand-500" />
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500">{month}</h3>
            <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-surface shadow-sm">
              {documents.map((document) => (
                <DocumentRow key={document.id} document={document} />
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}
