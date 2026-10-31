import { useId, useRef, useState, type DragEvent } from 'react'
import { UploadIcon } from '../../components/icons.tsx'
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
      className={`flex flex-col items-center gap-4 rounded-2xl border-2 border-dashed px-6 py-7 text-center transition-all sm:flex-row sm:text-left ${
        dragging ? 'scale-[1.01] border-brand-500 bg-brand-50' : 'border-slate-300 bg-surface hover:border-brand-400'
      }`}
    >
      <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-400 to-brand-700 text-white shadow-md shadow-brand-500/20">
        <UploadIcon className="h-7 w-7" />
      </span>
      <div className="flex-1">
        <p className="font-medium text-slate-900">Drag and drop your medical documents here</p>
        <p id={hintId} className="mt-1 text-sm text-slate-500">
          PDF, JPG or PNG · up to 10 MB each · lab reports, prescriptions, scans, photos
        </p>
      </div>
      <button
        type="button"
        aria-describedby={hintId}
        onClick={() => inputRef.current?.click()}
        className="shrink-0 rounded-lg bg-brand-600 px-4 py-2 font-medium text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
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
