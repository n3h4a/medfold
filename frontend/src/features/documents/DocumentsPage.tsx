import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { documentsApi, type DocumentType } from '../../api/documents.ts'
import { SearchIcon } from '../../components/icons.tsx'
import { PageHeader } from '../../components/ui.tsx'
import { DocumentList } from './DocumentList.tsx'
import { typeLabel } from './format.ts'
import { SearchResults } from './SearchResults.tsx'
import { UploadDropzone } from './UploadDropzone.tsx'
import { UploadQueue } from './UploadQueue.tsx'
import { useUploadQueue } from './useUploadQueue.ts'

/** Chips for the kinds of document the user actually has, with counts. */
function TypeFilter({ value, onChange }: { value: DocumentType | null; onChange: (type: DocumentType | null) => void }) {
  const { data } = useQuery({ queryKey: ['documents'], queryFn: () => documentsApi.list() })
  const counts = new Map<DocumentType, number>()
  for (const d of data?.items ?? []) if (d.docType) counts.set(d.docType, (counts.get(d.docType) ?? 0) + 1)
  if (counts.size < 2) return null

  const chip = (active: boolean) =>
    `shrink-0 rounded-full border px-3 py-1 text-sm font-medium transition-colors ${
      active
        ? 'border-brand-600 bg-brand-600 text-white'
        : 'border-slate-200 bg-surface text-slate-600 hover:border-slate-300 hover:text-slate-900'
    }`
  return (
    <div role="group" aria-label="Filter by type" className="flex gap-2 overflow-x-auto pb-1">
      <button type="button" aria-pressed={value === null} onClick={() => onChange(null)} className={chip(value === null)}>
        All
      </button>
      {[...counts.entries()].map(([type, count]) => (
        <button key={type} type="button" aria-pressed={value === type} onClick={() => onChange(type)} className={chip(value === type)}>
          {typeLabel[type]} <span className="opacity-70">{count}</span>
        </button>
      ))}
    </div>
  )
}

export function DocumentsPage() {
  const { items, addFiles, clearFinished } = useUploadQueue()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [type, setType] = useState<DocumentType | null>(null)

  // Search once typing pauses rather than on every keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), 250)
    return () => clearTimeout(timer)
  }, [input])

  return (
    <div className="animate-rise space-y-6">
      <PageHeader title="Your documents" subtitle="Upload once. Find everything. Understand your medical history." />
      <UploadDropzone onFiles={addFiles} />
      <UploadQueue items={items} onClear={clearFinished} />
      <div className="relative">
        <label htmlFor="document-search" className="sr-only">
          Search your documents
        </label>
        <SearchIcon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
        <input
          id="document-search"
          type="search"
          value={input}
          maxLength={100}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Search tests, medicines, doctors, hospitals or any text"
          className="w-full rounded-xl border border-slate-300 bg-surface py-3 pl-11 pr-3 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15"
        />
      </div>
      {query ? (
        <SearchResults query={query} />
      ) : (
        <>
          <TypeFilter value={type} onChange={setType} />
          <DocumentList type={type} />
        </>
      )}
    </div>
  )
}
