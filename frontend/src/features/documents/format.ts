import type { DocumentStatus, DocumentType, MedicalDocument } from '../../api/documents.ts'

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

/** Dates from the API are ISO strings; record dates have no time part and must not shift by time zone. */
export function formatDate(iso: string): string {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(iso) ? new Date(`${iso}T00:00:00`) : new Date(iso)
  return dateFormat.format(date)
}

export const statusLabel: Record<DocumentStatus, string> = {
  UPLOADED: 'Waiting to process',
  PROCESSING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

export const typeLabel: Record<DocumentType, string> = {
  LAB_REPORT: 'Lab report',
  PRESCRIPTION: 'Prescription',
  DISCHARGE_SUMMARY: 'Discharge summary',
  IMAGING_REPORT: 'Imaging report',
  CONSULTATION_NOTE: 'Consultation note',
  OTHER: 'Other',
}

export function displayTitle(document: MedicalDocument): string {
  return document.title ?? document.originalFilename
}
