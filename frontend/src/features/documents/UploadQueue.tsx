import type { UploadItem } from './useUploadQueue.ts'
import { formatBytes } from './format.ts'

function StateText({ item }: { item: UploadItem }) {
  switch (item.state) {
    case 'queued':
      return <span className="text-slate-500">Waiting…</span>
    case 'uploading':
      return <span className="text-slate-600">{Math.round(item.progress * 100)}%</span>
    case 'done':
      return <span className="font-medium text-emerald-700">Uploaded</span>
    case 'error':
      return <span className="font-medium text-red-700">{item.error}</span>
  }
}

export function UploadQueue({ items, onClear }: { items: UploadItem[]; onClear: () => void }) {
  if (items.length === 0) return null
  const hasFinished = items.some((item) => item.state === 'done' || item.state === 'error')

  return (
    <section aria-label="Uploads" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">Uploads</h2>
        {hasFinished && (
          <button type="button" onClick={onClear} className="text-sm font-medium text-brand-600 hover:underline">
            Clear finished
          </button>
        )}
      </div>
      <ul className="mt-3 space-y-3">
        {items.map((item) => (
          <li key={item.id} aria-label={item.name}>
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="min-w-0 truncate text-slate-800">
                {item.name} <span className="text-slate-400">· {formatBytes(item.size)}</span>
              </span>
              <span className="shrink-0 text-right" role="status">
                <StateText item={item} />
              </span>
            </div>
            {(item.state === 'uploading' || item.state === 'queued') && (
              <div
                className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-100"
                role="progressbar"
                aria-label={`Uploading ${item.name}`}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.round(item.progress * 100)}
              >
                <div className="h-full rounded-full bg-brand-500 transition-[width]" style={{ width: `${item.progress * 100}%` }} />
              </div>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
