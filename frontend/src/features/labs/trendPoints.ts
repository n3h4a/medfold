import type { LabResult } from '../../api/documents.ts'

export interface Point {
  result: LabResult
  value: number
  time: number
}

function dayTime(iso: string): number {
  return new Date(`${iso}T00:00:00Z`).getTime()
}

/**
 * Plottable points: dated, numeric, and in the same unit as the most recent one (values in
 * different units can't share an axis). Everything else stays in the table below the chart.
 */
export function plottablePoints(results: LabResult[]): { points: Point[]; unit: string | null; skipped: number } {
  const usable = results
    .filter((r) => r.valueNumeric !== null && r.observedOn !== null)
    .sort((a, b) => (a.observedOn as string).localeCompare(b.observedOn as string))
  const unit = usable.length > 0 ? usable[usable.length - 1].unit : null
  const points = usable
    .filter((r) => r.unit === unit)
    .map((r) => ({ result: r, value: r.valueNumeric as number, time: dayTime(r.observedOn as string) }))
  return { points, unit, skipped: results.length - points.length }
}
