import type { LabResult } from '../../api/documents.ts'
import { toUnit } from './units.ts'

export interface Point {
  result: LabResult
  /** In the chart's unit; differs from the printed value only when {@link converted}. */
  value: number
  time: number
  converted: boolean
}

function dayTime(iso: string): number {
  return new Date(`${iso}T00:00:00Z`).getTime()
}

/**
 * Plottable points: dated, numeric, and in the unit of the most recent one, or convertible to it
 * by a standard conversion for this test. Everything else stays in the table below the chart.
 */
export function plottablePoints(results: LabResult[]): {
  points: Point[]
  unit: string | null
  skipped: number
  converted: number
} {
  const usable = results
    .filter((r) => r.valueNumeric !== null && r.observedOn !== null)
    .sort((a, b) => (a.observedOn as string).localeCompare(b.observedOn as string))
  const unit = usable.length > 0 ? usable[usable.length - 1].unit : null
  const points: Point[] = []
  for (const r of usable) {
    const value = toUnit(r.testKey, r.valueNumeric as number, r.unit, unit)
    if (value === null) continue
    points.push({ result: r, value, time: dayTime(r.observedOn as string), converted: value !== r.valueNumeric })
  }
  return {
    points,
    unit,
    skipped: results.length - points.length,
    converted: points.filter((p) => p.converted).length,
  }
}

/** A converted value shown with sensible precision. */
export function formatConverted(value: number): string {
  return String(Number(value.toPrecision(3)))
}
