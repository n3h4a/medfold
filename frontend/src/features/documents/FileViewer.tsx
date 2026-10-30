import { useQuery } from '@tanstack/react-query'
import { documentsApi, type MedicalDocument } from '../../api/documents.ts'
import { retryUnlessClientError } from '../../api/retry.ts'
import { errorMessage } from '../../auth/errors.ts'
import { displayTitle } from './format.ts'
import { useObjectUrl } from './useObjectUrl.ts'

export function FileViewer({ document }: { document: MedicalDocument }) {
  const file = useQuery({
    queryKey: ['documents', document.id, 'file'],
    queryFn: () => documentsApi.file(document.id),
    staleTime: Infinity,
    retry: retryUnlessClientError,
    // Don't keep the medical file in memory after leaving the page.
    gcTime: 0,
  })
  const url = useObjectUrl(file.data)
  const title = displayTitle(document)
  const isPdf = document.mimeType === 'application/pdf'

  return (
    <section
      aria-label="Original file"
      className={`flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm ${isPdf ? 'min-h-[60vh]' : 'min-h-48'}`}
    >
      <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-2.5">
        <p className="truncate text-sm font-medium text-slate-700">{document.originalFilename}</p>
        {url && (
          <div className="flex shrink-0 gap-3 text-sm font-medium">
            <a href={url} target="_blank" rel="noopener" className="text-brand-600 hover:underline">
              Open
            </a>
            <a href={url} download={document.originalFilename} className="text-brand-600 hover:underline">
              Download
            </a>
          </div>
        )}
      </div>
      <div className="flex flex-1 items-center justify-center bg-slate-50">
        {file.isPending && <p className="text-sm text-slate-500" role="status">Loading file…</p>}
        {file.isError && (
          <p role="alert" className="px-4 text-sm text-red-700">
            {errorMessage(file.error)}
          </p>
        )}
        {url && isPdf && <iframe src={url} title={`${title} (PDF)`} className="h-full min-h-[60vh] w-full flex-1 bg-white" />}
        {url && !isPdf && <img src={url} alt={title} className="max-h-[75vh] max-w-full object-contain" />}
      </div>
    </section>
  )
}
