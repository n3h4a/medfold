import { useId, useRef, useState, type DragEvent } from 'react'
import { ACCEPT_ATTRIBUTE } from './validateFile.ts'

export function UploadDropzone({ onFiles }: { onFiles: (files: File[]) => void }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const hintId = useId()

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    if (event.dataTransfer.files.length > 0) onFiles(Array.from(event.dataTransfer.files))
  }

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      className={`rounded-xl border-2 border-dashed px-6 py-8 text-center transition-colors ${
        dragging ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-white'
      }`}
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" className="mx-auto h-10 w-10 text-brand-600" fill="none" stroke="currentColor" strokeWidth="1.5">
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 16V4m0 0-4 4m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
      </svg>
      <p className="mt-3 font-medium text-slate-900">Drag and drop your medical documents here</p>
      <p id={hintId} className="mt-1 text-sm text-slate-500">
        PDF, JPG or PNG · up to 10 MB each
      </p>
      <button
        type="button"
        aria-describedby={hintId}
        onClick={() => inputRef.current?.click()}
        className="mt-4 rounded-lg bg-brand-600 px-4 py-2 font-medium text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
      >
        Choose files
      </button>
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ACCEPT_ATTRIBUTE}
        className="sr-only"
        tabIndex={-1}
        aria-label="Upload documents"
        onChange={(event) => {
          if (event.target.files && event.target.files.length > 0) onFiles(Array.from(event.target.files))
          event.target.value = ''
        }}
      />
    </div>
  )
}
