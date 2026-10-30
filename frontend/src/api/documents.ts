import {
  API_BASE,
  ApiError,
  apiDelete,
  apiGet,
  apiGetBlob,
  apiPost,
  parseText,
  withAuthRecovery,
  type RawResponse,
} from './client.ts'

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'
export type DocumentType =
  | 'LAB_REPORT'
  | 'PRESCRIPTION'
  | 'DISCHARGE_SUMMARY'
  | 'IMAGING_REPORT'
  | 'CONSULTATION_NOTE'
  | 'OTHER'

export interface MedicalDocument {
  id: string
  originalFilename: string
  mimeType: string
  sizeBytes: number
  status: DocumentStatus
  failureReason: string | null
  pageCount: number | null
  docType: DocumentType | null
  docDate: string | null
  title: string | null
  providerName: string | null
  facilityName: string | null
  summary: string | null
  userEdited: boolean
  createdAt: string
  updatedAt: string
}

/** Where a value sits relative to the reference range printed on the same report. Computed by the backend. */
export type LabFlag = 'LOW' | 'NORMAL' | 'HIGH'

export interface LabResult {
  id: string
  documentId: string
  testName: string
  testKey: string
  valueText: string
  valueNumeric: number | null
  unit: string | null
  refText: string | null
  refLow: number | null
  refHigh: number | null
  flag: LabFlag | null
  observedOn: string | null
  pageNumber: number | null
  userEdited: boolean
}

export interface Medication {
  id: string
  documentId: string
  name: string
  dosage: string | null
  frequency: string | null
  route: string | null
  duration: string | null
  instructions: string | null
  prescribedOn: string | null
  userEdited: boolean
}

export interface TermExplanation {
  term: string
  definition: string
}

export interface DocumentResults {
  labResults: LabResult[]
  medications: Medication[]
  terms: TermExplanation[]
}

export interface DocumentStatusResponse {
  id: string
  status: DocumentStatus
  failureReason: string | null
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export const documentsApi = {
  list: (page = 0, size = 50) => apiGet<PageResponse<MedicalDocument>>(`/documents?page=${page}&size=${size}`),
  get: (id: string) => apiGet<MedicalDocument>(`/documents/${id}`),
  remove: (id: string) => apiDelete(`/documents/${id}`),
  file: (id: string) => apiGetBlob(`/documents/${id}/file`),
  results: (id: string) => apiGet<DocumentResults>(`/documents/${id}/results`),
  reprocess: (id: string) => apiPost<DocumentStatusResponse>(`/documents/${id}/reprocess`),
}

interface UploadOptions {
  /** Called with 0..1 as the file is sent. */
  onProgress?: (fraction: number) => void
}

function xhrUpload(file: File, token: string | null, onProgress?: (fraction: number) => void): Promise<RawResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_BASE}/documents`)
    xhr.setRequestHeader('Accept', 'application/json')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) onProgress(event.loaded / event.total)
    }
    xhr.onload = () => resolve({ status: xhr.status, body: parseText(xhr.responseText) })
    xhr.onerror = () => reject(new TypeError('Network request failed'))
    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}

/**
 * Uploads one file with progress reporting. Uses XMLHttpRequest because fetch cannot report
 * upload progress; token refresh works exactly as for every other request.
 */
export async function uploadDocument(file: File, { onProgress }: UploadOptions = {}): Promise<MedicalDocument> {
  const response = await withAuthRecovery('/documents', (token) => {
    onProgress?.(0)
    return xhrUpload(file, token, onProgress)
  })
  if (response.status < 200 || response.status >= 300) {
    throw new ApiError(response.status, response.body)
  }
  return response.body as MedicalDocument
}
