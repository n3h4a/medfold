import { useLayoutEffect, useRef, useState } from 'react'
import type { LabResult } from '../../api/documents.ts'
import { formatDate } from '../documents/format.ts'
import { formatConverted, plottablePoints, type Point } from './trendPoints.ts'

// Validated single-series color (lightness, chroma and contrast on the white card surface).
const LINE = '#009c8c'
const DEFAULT_WIDTH = 600
const HEIGHT = 220
const TOOLTIP = { width: 170, height: 38, convertedHeight: 54 }

/** Draws at the container's real pixel width so text stays 11-12px on phones and wide screens alike. */
function useWidth() {
  const ref = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(DEFAULT_WIDTH)
  useLayoutEffect(() => {
    const element = ref.current
    if (!element || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(Math.round(entry.contentRect.width), 240)))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return { ref, width }
}
const PAD = { top: 16, right: 16, bottom: 28, left: 44 }

/** About four round tick values covering [min, max]. */
function niceTicks(min: number, max: number): number[] {
  const span = max - min || Math.abs(max) || 1
  const raw = span / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw
  const ticks: number[] = []
  for (let t = Math.ceil(min / step) * step; t <= max + step * 1e-9; t += step) {
    ticks.push(Number(t.toFixed(10)))
  }
  return ticks
}

/** One test over time, with the reference range printed on the latest report as a shaded band. */
export function LabTrendChart({ testName, results }: { testName: string; results: LabResult[] }) {
  const [active, setActive] = useState<number | null>(null)
  const { ref, width: WIDTH } = useWidth()
  const { points, unit, skipped, converted } = plottablePoints(results)
  if (points.length < 2) return null

  const latest = points[points.length - 1].result
  const band = { low: latest.refLow, high: latest.refHigh }
  const values = points.map((p) => p.value)
  const lo = Math.min(...values, band.low ?? Infinity)
  const hi = Math.max(...values, band.high ?? -Infinity)
  const margin = (hi - lo || Math.abs(hi) || 1) * 0.15
  const ticks = niceTicks(lo - margin, hi + margin)
  const yMin = Math.min(ticks[0], lo - margin)
  const yMax = Math.max(ticks[ticks.length - 1], hi + margin)
  const tMin = points[0].time
  const tMax = points[points.length - 1].time

  const plotW = WIDTH - PAD.left - PAD.right
  const plotH = HEIGHT - PAD.top - PAD.bottom
  const x = (t: number) => PAD.left + (tMax === tMin ? plotW / 2 : ((t - tMin) / (tMax - tMin)) * plotW)
  const y = (v: number) => PAD.top + (1 - (v - yMin) / (yMax - yMin)) * plotH
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.time).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
  const bandTop = y(band.high ?? yMax)
  const bandBottom = y(band.low ?? yMin)
  const hasBand = band.low !== null || band.high !== null
  const unitLabel = unit ? ` ${unit}` : ''
  const shown = (p: Point) => (p.converted ? `${formatConverted(p.value)}${unitLabel}` : `${p.result.valueText}${unitLabel}`)
  const printed = (p: Point) => `${p.result.valueText}${p.result.unit ? ` ${p.result.unit}` : ''}`
  const activePoint = active === null ? null : points[active]
  const tooltipHeight = activePoint?.converted ? TOOLTIP.convertedHeight : TOOLTIP.height

  return (
    <figure className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <figcaption className="text-sm font-semibold text-slate-900">
        {testName} over time{unit && <span className="font-normal text-slate-500"> ({unit})</span>}
      </figcaption>
      <div ref={ref} className="mt-2 w-full">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          width={WIDTH}
          height={HEIGHT}
          className="block max-w-full"
          role="img"
          aria-label={`${testName}: ${points.length} values from ${formatDate(points[0].result.observedOn as string)} to ${formatDate(latest.observedOn as string)}. All values are listed in the table below.`}
          onMouseLeave={() => setActive(null)}
        >
          {hasBand && (
            <rect x={PAD.left} width={plotW} y={bandTop} height={Math.max(bandBottom - bandTop, 0)} className="fill-slate-100" />
          )}
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={WIDTH - PAD.right} y1={y(t)} y2={y(t)} className="stroke-slate-200" strokeWidth={1} />
              <text x={PAD.left - 6} y={y(t)} textAnchor="end" dominantBaseline="middle" className="fill-slate-500 text-[11px]">
                {t}
              </text>
            </g>
          ))}
          <text x={PAD.left} y={HEIGHT - 8} className="fill-slate-500 text-[11px]">
            {formatDate(points[0].result.observedOn as string)}
          </text>
          <text x={WIDTH - PAD.right} y={HEIGHT - 8} textAnchor="end" className="fill-slate-500 text-[11px]">
            {formatDate(latest.observedOn as string)}
          </text>
          {activePoint && (
            <line
              x1={x(activePoint.time)}
              x2={x(activePoint.time)}
              y1={PAD.top}
              y2={PAD.top + plotH}
              className="stroke-slate-300"
              strokeWidth={1}
            />
          )}
          <path d={path} fill="none" stroke={LINE} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
          {points.map((p, i) => (
            <g key={p.result.id}>
              <circle cx={x(p.time)} cy={y(p.value)} r={4} fill={LINE} stroke="white" strokeWidth={2} />
              {/* Larger invisible target for hover and keyboard focus. */}
              <circle
                cx={x(p.time)}
                cy={y(p.value)}
                r={14}
                fill="transparent"
                tabIndex={0}
                aria-label={`${formatDate(p.result.observedOn as string)}: ${shown(p)}${p.converted ? ` (printed as ${printed(p)})` : ''}`}
                onMouseEnter={() => setActive(i)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
                className="cursor-pointer focus:outline-none"
              />
            </g>
          ))}
          {activePoint && (
            <g
              transform={`translate(${Math.min(Math.max(x(activePoint.time) - TOOLTIP.width / 2, PAD.left), WIDTH - PAD.right - TOOLTIP.width)},${
                y(activePoint.value) < PAD.top + plotH / 2
                  ? y(activePoint.value) + 12
                  : y(activePoint.value) - 12 - tooltipHeight
              })`}
              pointerEvents="none"
            >
              <rect width={TOOLTIP.width} height={tooltipHeight} rx={6} className="fill-white stroke-slate-300" />
              <text x={10} y={16} className="fill-slate-500 text-[11px]">
                {formatDate(activePoint.result.observedOn as string)}
              </text>
              <text x={10} y={31} className="fill-slate-900 text-[12px] font-semibold">
                {shown(activePoint)}
              </text>
              {activePoint.converted && (
                <text x={10} y={47} className="fill-slate-500 text-[11px]">
                  printed as {printed(activePoint)}
                </text>
              )}
            </g>
          )}
        </svg>
      </div>
      <p className="mt-2 text-xs text-slate-500">
        {hasBand && <>Shaded: the reference range printed on the latest report ({latest.refText}). </>}
        {converted > 0 && (
          <>
            {converted} value(s) printed in another unit are converted to {unit} for the chart; the table shows them as
            printed.{' '}
          </>
        )}
        {skipped > 0 && <>{skipped} result(s) without a date, a number or a convertible unit are only in the table. </>}
        Ranges can differ between labs.
      </p>
    </figure>
  )
}
