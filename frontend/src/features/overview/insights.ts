import type { Medication } from '../../api/documents.ts'
import type { LabTestSummary } from '../../api/records.ts'

/** Medicines from the most recent prescription date: what the user was last given. */
export function latestMedicines(medications: Medication[]): { date: string | null; items: Medication[] } {
  const dated = medications.filter((m) => m.prescribedOn)
  if (dated.length === 0) return { date: null, items: medications.slice(0, 5) }
  const date = dated.map((m) => m.prescribedOn as string).sort().at(-1) as string
  return { date, items: dated.filter((m) => m.prescribedOn === date) }
}

/** Tests whose most recent result is outside the range printed on that report. */
export function outsideRange(tests: LabTestSummary[]): LabTestSummary[] {
  return tests.filter((t) => t.latestFlag === 'HIGH' || t.latestFlag === 'LOW')
}
