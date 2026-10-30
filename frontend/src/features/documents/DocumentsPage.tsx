import { useEffect, useState } from 'react'
import { DocumentList } from './DocumentList.tsx'
import { SearchResults } from './SearchResults.tsx'
import { UploadDropzone } from './UploadDropzone.tsx'
import { UploadQueue } from './UploadQueue.tsx'
import { useUploadQueue } from './useUploadQueue.ts'

export function DocumentsPage() {
  const { items, addFiles, clearFinished } = useUploadQueue()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')

  // Search once typing pauses rather than on every keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), 250)
    return () => clearTimeout(timer)
  }, [input])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Your documents</h1>
        <p className="mt-1 text-slate-600">Upload once. Find everything. Understand your medical history.</p>
      </div>
      <UploadDropzone onFiles={addFiles} />
      <UploadQueue items={items} onClear={clearFinished} />
      <div>
        <label htmlFor="document-search" className="sr-only">
          Search your documents
        </label>
        <input
          id="document-search"
          type="search"
          value={input}
          maxLength={100}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Search tests, medicines, doctors, hospitals or any text"
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
        />
      </div>
      {query ? <SearchResults query={query} /> : <DocumentList />}
    </div>
  )
}
