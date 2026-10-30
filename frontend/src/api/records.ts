import { apiGet } from './client.ts'
import type { LabFlag, LabResult, Medication } from './documents.ts'

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
}

/** Every result of one test across the user's reports, oldest first, undated last. */
export interface LabTrend {
  testKey: string
  testName: string
  results: LabResult[]
}

export const recordsApi = {
  labTests: () => apiGet<LabTestSummary[]>('/labs'),
  labTrend: (testKey: string) => apiGet<LabTrend>(`/labs/${encodeURIComponent(testKey)}`),
  medications: () => apiGet<Medication[]>('/medications'),
}
