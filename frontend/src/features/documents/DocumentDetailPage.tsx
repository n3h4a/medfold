import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { documentsApi, type MedicalDocument } from '../../api/documents.ts'
import { retryUnlessClientError } from '../../api/retry.ts'
import { errorMessage } from '../../auth/errors.ts'
import { FullPageSpinner } from '../../components/FullPageSpinner.tsx'
import { FileViewer } from './FileViewer.tsx'
import { DocumentDetailsForm } from './EditForms.tsx'
import { ExtractedResults } from './ExtractedResults.tsx'
import { displayTitle, formatBytes, formatDate, isInProgress, polling, typeLabel } from './format.ts'
import { StatusPill } from './StatusPill.tsx'

function BackLink() {
  return (
    <Link to="/" className="inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline">
      <span aria-hidden="true">←</span> All documents
    </Link>
  )
}

function Field({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="py-2">
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className={`mt-0.5 text-sm ${value ? 'text-slate-900' : 'text-slate-400'}`}>{value ?? 'Not found yet'}</dd>
    </div>
  )
}

/** Queues the document again; the page then follows its progress by polling. */
function useReprocess(document: MedicalDocument) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => documentsApi.reprocess(document.id),
    onSuccess: (status) => {
      queryClient.setQueryData<MedicalDocument>(['documents', document.id], (current) =>
        current ? { ...current, status: status.status, failureReason: status.failureReason } : current,
      )
      return queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })
}

function DetailsPanel({ document }: { document: MedicalDocument }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const remove = useMutation({
    mutationFn: () => documentsApi.remove(document.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      navigate('/', { replace: true })
    },
  })
  const reprocess = useReprocess(document)
  const [editing, setEditing] = useState(false)
  const title = displayTitle(document)

  if (editing) {
    return (
      <aside className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <DocumentDetailsForm document={document} onDone={() => setEditing(false)} />
      </aside>
    )
  }

  return (
    <aside className="space-y-4">
      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-xs text-slate-500">{document.userEdited ? 'Edited by you' : 'Read from the document'}</span>
          {!isInProgress(document.status) && (
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="rounded px-1.5 py-0.5 text-sm font-medium text-brand-700 hover:bg-brand-50 focus:outline-none focus:ring-2 focus:ring-brand-500"
            >
              Edit details
            </button>
          )}
        </div>
        <dl className="divide-y divide-slate-100">
          <Field label="Document type" value={document.docType ? typeLabel[document.docType] : null} />
          <Field label="Record date" value={document.docDate ? formatDate(document.docDate) : null} />
          <Field label="Doctor" value={document.providerName} />
          <Field label="Hospital or lab" value={document.facilityName} />
          <Field label="Uploaded" value={formatDate(document.createdAt)} />
          <Field
            label="File"
            value={[formatBytes(document.sizeBytes), document.pageCount ? `${document.pageCount} ${document.pageCount === 1 ? "page" : "pages"}` : null]
              .filter(Boolean)
              .join(' · ')}
          />
        </dl>
      </section>

      <section aria-labelledby="summary-heading" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <h2 id="summary-heading" className="text-sm font-semibold text-slate-900">
          Summary
        </h2>
        {document.summary ? (
          <>
            <p className="mt-2 whitespace-pre-line text-sm text-slate-700">{document.summary}</p>
            <p className="mt-3 text-xs text-slate-500">
              Generated automatically from your document. It describes the record and is not medical advice.
            </p>
          </>
        ) : (
          <p className="mt-2 text-sm text-slate-500">
            A plain-language summary will appear here once the document has been processed.
          </p>
        )}
      </section>

      <div className="flex flex-wrap gap-2">
        {document.status === 'READY' && (
          <button
            type="button"
            disabled={reprocess.isPending}
            onClick={() => {
              if (window.confirm('Read this document again? The extracted details will be replaced.')) reprocess.mutate()
            }}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-500 disabled:opacity-50"
          >
            Process again
          </button>
        )}
        <button
          type="button"
          disabled={remove.isPending}
          onClick={() => {
            if (window.confirm(`Delete "${title}"? This cannot be undone.`)) remove.mutate()
          }}
          className="rounded-lg border border-red-200 px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-400 disabled:opacity-50"
        >
          Delete document
        </button>
        {remove.isError && <p className="w-full text-sm text-red-700">{errorMessage(remove.error)}</p>}
        {reprocess.isError && <p className="w-full text-sm text-red-700">{errorMessage(reprocess.error)}</p>}
      </div>
    </aside>
  )
}

function FailureNotice({ document }: { document: MedicalDocument }) {
  const reprocess = useReprocess(document)
  return (
    <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
      <span>{document.failureReason ?? 'This document could not be processed.'}</span>
      <button
        type="button"
        disabled={reprocess.isPending}
        onClick={() => reprocess.mutate()}
        className="rounded-lg border border-red-300 bg-white px-3 py-1 font-medium text-red-800 hover:bg-red-100 focus:outline-none focus:ring-2 focus:ring-red-400 disabled:opacity-50"
      >
        Try again
      </button>
      {reprocess.isError && <span className="w-full">{errorMessage(reprocess.error)}</span>}
    </div>
  )
}

export function DocumentDetailPage() {
  const { id = '' } = useParams()
  const { data: document, isPending, isError, error } = useQuery({
    queryKey: ['documents', id],
    queryFn: () => documentsApi.get(id),
    retry: retryUnlessClientError,
    // Follow background processing until the document is ready or has failed.
    refetchInterval: (query) => (isInProgress(query.state.data?.status) ? polling.intervalMs : false),
  })

  if (isPending) return <FullPageSpinner />
  if (isError) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400)
    return (
      <div className="space-y-4">
        <BackLink />
        <div className="rounded-xl border border-slate-200 bg-white px-6 py-10 text-center">
          <h1 className="font-medium text-slate-900">{notFound ? 'Document not found' : 'Could not load this document'}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {notFound ? 'It may have been deleted, or the link is wrong.' : errorMessage(error)}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <BackLink />
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="min-w-0 break-words text-2xl font-semibold tracking-tight text-slate-900">{displayTitle(document)}</h1>
        <StatusPill status={document.status} />
      </div>
      {document.status === 'FAILED' && <FailureNotice document={document} />}
      {isInProgress(document.status) && (
        <p role="status" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Reading this document. Details will appear here automatically when it's done.
        </p>
      )}
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <FileViewer document={document} />
        <DetailsPanel document={document} />
      </div>
      <ExtractedResults document={document} />
    </div>
  )
}
