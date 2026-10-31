import type { DocumentType } from '../../api/documents.ts'
import { ActivityIcon, ClipboardIcon, DocumentIcon, FlaskIcon, PillIcon, ScanIcon } from '../../components/icons.tsx'

const styles: Record<DocumentType | 'UNKNOWN', { className: string; Icon: typeof DocumentIcon }> = {
  LAB_REPORT: { className: 'bg-sky-50 text-sky-700', Icon: FlaskIcon },
  PRESCRIPTION: { className: 'bg-violet-50 text-violet-700', Icon: PillIcon },
  IMAGING_REPORT: { className: 'bg-amber-50 text-amber-800', Icon: ScanIcon },
  DISCHARGE_SUMMARY: { className: 'bg-red-50 text-red-700', Icon: ClipboardIcon },
  CONSULTATION_NOTE: { className: 'bg-emerald-50 text-emerald-700', Icon: ActivityIcon },
  OTHER: { className: 'bg-slate-100 text-slate-600', Icon: DocumentIcon },
  UNKNOWN: { className: 'bg-slate-100 text-slate-600', Icon: DocumentIcon },
}

/** A colour-coded icon per kind of document; shows PDF/IMG until the type is known. */
export function DocumentTypeIcon({ type, mimeType }: { type: DocumentType | null; mimeType: string }) {
  const { className, Icon } = styles[type ?? 'UNKNOWN']
  return (
    <div aria-hidden="true" className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${className}`}>
      {type ? (
        <Icon className="h-5 w-5" />
      ) : (
        <span className="text-[10px] font-semibold">{mimeType === 'application/pdf' ? 'PDF' : 'IMG'}</span>
      )}
    </div>
  )
}
