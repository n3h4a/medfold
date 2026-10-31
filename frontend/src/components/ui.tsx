import type { ReactNode } from 'react'
import type { LabFlag } from '../api/documents.ts'

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1.5 text-slate-600">{subtitle}</p>}
      </div>
      {actions && <div className="no-print flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl border border-slate-200 bg-surface shadow-sm ${className}`}>{children}</div>
}

export function EmptyState({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-300 bg-surface px-6 py-12 text-center">
      {icon && (
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-ink">
          {icon}
        </div>
      )}
      <p className="font-medium text-slate-900">{title}</p>
      {children && <div className="mt-1 text-sm text-slate-500">{children}</div>}
    </div>
  )
}

export function Problem({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
      {children}
    </p>
  )
}

/** Placeholder rows while a list loads; announced once to screen readers. */
export function SkeletonList({ rows = 4, label }: { rows?: number; label: string }) {
  return (
    <div role="status" aria-label={label} className="space-y-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-3 rounded-xl border border-slate-200 bg-surface p-4">
          <div className="skeleton h-10 w-10" />
          <div className="flex-1 space-y-2">
            <div className="skeleton h-3.5 w-2/5" />
            <div className="skeleton h-3 w-1/4" />
          </div>
        </div>
      ))}
      <span className="sr-only">{label}</span>
    </div>
  )
}

const flagStyles: Record<LabFlag, string> = {
  HIGH: 'bg-amber-100 text-amber-900',
  LOW: 'bg-sky-100 text-sky-800',
  NORMAL: 'bg-emerald-100 text-emerald-800',
}
const flagShort: Record<LabFlag, string> = { HIGH: 'Above range', LOW: 'Below range', NORMAL: 'In range' }
const flagLong: Record<LabFlag, string> = {
  HIGH: 'Above printed range',
  LOW: 'Below printed range',
  NORMAL: 'Within printed range',
}

/** Compact comparison with the printed range. Deliberately not red: it is information, not an alarm. */
export function FlagBadge({ flag, long = false }: { flag: LabFlag | null; long?: boolean }) {
  if (!flag) return <span className="text-xs text-slate-400">{long ? '—' : 'No range'}</span>
  return (
    <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${flagStyles[flag]}`}>
      {flag !== 'NORMAL' && <span aria-hidden="true">{flag === 'HIGH' ? '↑' : '↓'}</span>}
      <span>{long ? flagLong[flag] : flagShort[flag]}</span>
    </span>
  )
}

/** A tiny trend line; the last point is emphasised. Purely visual: the numbers are shown next to it. */
export function Sparkline({ values, className = 'h-8 w-24' }: { values: number[]; className?: string }) {
  if (values.length < 2) return null
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const x = (i: number) => 2 + (i / (values.length - 1)) * 96
  const y = (v: number) => 26 - ((v - min) / span) * 22
  const d = values.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
  const last = values.length - 1
  return (
    <svg aria-hidden="true" viewBox="0 0 100 30" preserveAspectRatio="none" className={className}>
      <path d={`${d} L${x(last)},30 L${x(0)},30 Z`} className="fill-brand-500/10" />
      <path d={d} fill="none" className="stroke-brand-500" strokeWidth="2" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
      <circle cx={x(last)} cy={y(values[last])} r="2.6" className="fill-brand-500" />
    </svg>
  )
}

export function StatCard({
  label,
  value,
  detail,
  icon,
  tone = 'brand',
}: {
  label: string
  value: ReactNode
  detail?: ReactNode
  icon: ReactNode
  tone?: 'brand' | 'amber' | 'sky' | 'violet'
}) {
  const tones = {
    brand: 'bg-brand-50 text-brand-ink',
    amber: 'bg-amber-50 text-amber-800',
    sky: 'bg-sky-50 text-sky-700',
    violet: 'bg-violet-50 text-violet-700',
  }
  return (
    <Card className="p-5 transition-shadow hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium text-slate-600">{label}</p>
        <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${tones[tone]}`}>{icon}</span>
      </div>
      <p className="mt-2 text-3xl font-semibold tracking-tight text-slate-900">{value}</p>
      {detail && <p className="mt-1 text-xs text-slate-500">{detail}</p>}
    </Card>
  )
}
