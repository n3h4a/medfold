import type { LabResult, MedicalDocument, Medication } from '../api/documents.ts'
import type { LabTestSummary } from '../api/records.ts'

export function documentFixture(overrides: Partial<MedicalDocument> = {}): MedicalDocument {
  return {
    id: 'doc-1', originalFilename: 'cbc.pdf', mimeType: 'application/pdf', sizeBytes: 2048, status: 'READY',
    failureReason: null, pageCount: 1, docType: 'LAB_REPORT', docDate: '2026-03-02', title: 'Complete Blood Count',
    providerName: null, facilityName: null, summary: null, userEdited: false, createdAt: '2026-03-03T10:00:00Z',
    updatedAt: '2026-03-03T10:00:00Z', ...overrides,
  }
}

export function testSummary(overrides: Partial<LabTestSummary> = {}): LabTestSummary {
  return {
    testKey: 'hba1c', testName: 'HbA1c', unit: '%', resultCount: 2, latestValueText: '6.1', latestFlag: 'HIGH',
    latestObservedOn: '2026-06-01', latestDocumentId: 'doc-2', latestRefText: '4.0 - 5.6', previousValueText: '5.4',
    previousObservedOn: '2025-06-01', ...overrides,
  }
}

export function labResult(overrides: Partial<LabResult> = {}): LabResult {
  return {
    id: 'lab-1', documentId: 'doc-1', testName: 'HbA1c', testKey: 'hba1c', valueText: '5.4', valueNumeric: 5.4,
    unit: '%', refText: '4.0 - 5.6', refLow: 4, refHigh: 5.6, flag: 'NORMAL', observedOn: '2025-06-01', pageNumber: 1,
    userEdited: false, ...overrides,
  }
}

export function medication(overrides: Partial<Medication> = {}): Medication {
  return {
    id: 'med-1', documentId: 'doc-7', name: 'Metformin', dosage: '500 mg', frequency: '1-0-1', route: 'Oral',
    duration: '30 days', instructions: 'After food', prescribedOn: '2026-05-20', userEdited: false, ...overrides,
  }
}

export function page<T>(items: T[]) {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1 }
}
