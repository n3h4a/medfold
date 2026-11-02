import { apiGet } from './client.ts'
import type { LabFlag, LabResult, MedicalDocument, Medication } from './documents.ts'

/** One test the user has results for, with its most recent value. */
export interface LabTestSummary {
  testKey: string
  testName: string
  unit: string | null
  resultCount: number
  latestValueText: string
  latestFlag: LabFlag | null
  latestObservedOn: string | null
  latestDocumentId: string
  latestRefText: string | null
  /** The dated result before the latest one, if any. */
  previousValueText: string | null
  previousObservedOn: string | null
}

/** Every result of one test across the user's reports, oldest first, undated last. */
export interface LabTrend {
  testKey: string
  testName: string
  results: LabResult[]
}

/**
 * A document matching every search word, as typed or as a similar spelling. snippet is page text around
 * the first word, if the text matched.
 */
export interface SearchResult {
  document: MedicalDocument
  snippet: string | null
  pageNumber: number | null
  /** The text in the snippet that matched each word, as typed or as a similar spelling. */
  highlights: string[]
  /** False when at least one word only matched a similar spelling ("haemoglobn" → "Haemoglobin"). */
  exact: boolean
}

export const recordsApi = {
  labTests: () => apiGet<LabTestSummary[]>('/labs'),
  labTrend: (testKey: string) => apiGet<LabTrend>(`/labs/${encodeURIComponent(testKey)}`),
  medications: () => apiGet<Medication[]>('/medications'),
  search: (q: string) => apiGet<SearchResult[]>(`/search?q=${encodeURIComponent(q)}`),
}
