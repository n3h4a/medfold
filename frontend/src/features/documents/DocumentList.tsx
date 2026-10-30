import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { documentsApi, type MedicalDocument } from '../../api/documents.ts'
import { errorMessage } from '../../auth/errors.ts'
import { displayTitle, formatBytes, formatDate, typeLabel } from './format.ts'
import { StatusPill } from './StatusPill.tsx'

function FileIcon({ mimeType }: { mimeType: string }) {
  const isPdf = mimeType === 'application/pdf'
  return (
    <div
      aria-hidden="true"
      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-xs font-semibold ${
        isPdf ? 'bg-red-50 text-red-700' : 'bg-sky-50 text-sky-700'
      }`}
    >
      {isPdf ? 'PDF' : 'IMG'}
    </div>
  )
}

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
    <li className="relative flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
      <FileIcon mimeType={document.mimeType} />
      <div className="min-w-0 flex-1">
        <Link
          to={`/documents/${document.id}`}
          className="block truncate font-medium text-slate-900 after:absolute after:inset-0 hover:text-brand-700 focus:outline-none focus-visible:underline"
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

export function DocumentList() {
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['documents'],
    queryFn: () => documentsApi.list(),
  })

  if (isPending) {
    return <p className="text-sm text-slate-500" role="status">Loading your documents…</p>
  }
  if (isError) {
    return (
      <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
        {errorMessage(error)}
      </p>
    )
  }
  if (data.items.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white px-6 py-10 text-center">
        <p className="font-medium text-slate-900">No documents yet</p>
        <p className="mt-1 text-sm text-slate-500">Upload a lab report, prescription or discharge summary to get started.</p>
      </div>
    )
  }

  return (
    <section aria-labelledby="documents-heading">
      <h2 id="documents-heading" className="mb-2 text-sm font-semibold text-slate-900">
        {data.totalItems} {data.totalItems === 1 ? 'document' : 'documents'}
      </h2>
      <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        {data.items.map((document) => (
          <DocumentRow key={document.id} document={document} />
        ))}
      </ul>
    </section>
  )
}
