import type { DocumentStatus } from '../../api/documents.ts'
import { statusLabel } from './format.ts'

const statusClasses: Record<DocumentStatus, string> = {
  UPLOADED: 'bg-slate-100 text-slate-700',
  PROCESSING: 'bg-amber-100 text-amber-800',
  READY: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
}

export function StatusPill({ status, className = '' }: { status: DocumentStatus; className?: string }) {
  return (
    <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${statusClasses[status]} ${className}`}>
      {statusLabel[status]}
    </span>
  )
}
