import { useQuery } from '@tanstack/react-query'
import { fetchHealth } from '../../api/health.ts'

type Tone = 'ok' | 'bad' | 'pending'

const toneClasses: Record<Tone, string> = {
  ok: 'bg-emerald-100 text-emerald-800',
  bad: 'bg-red-100 text-red-800',
  pending: 'bg-slate-100 text-slate-600',
}

function StatusRow({ label, value, tone }: { label: string; value: string; tone: Tone }) {
  return (
    <div className="flex items-center justify-between py-2">
      <dt className="text-sm text-slate-600">{label}</dt>
      <dd className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${toneClasses[tone]}`}>
        {value}
      </dd>
    </div>
  )
}

export function SystemStatus() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
    refetchInterval: 15_000,
    retry: false,
  })

  let backend: [string, Tone] = ['Checking…', 'pending']
  let database: [string, Tone] = ['Checking…', 'pending']

  if (isError) {
    backend = ['Unreachable', 'bad']
    database = ['Unknown', 'pending']
  } else if (!isPending) {
    backend = ['Online', 'ok']
    database = data.database === 'UP' ? ['Connected', 'ok'] : ['Unavailable', 'bad']
  }

  return (
    <section
      aria-labelledby="system-status-heading"
      className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
    >
      <h2 id="system-status-heading" className="text-sm font-semibold text-slate-900">
        System status
      </h2>
      <dl className="mt-2 divide-y divide-slate-100">
        <StatusRow label="Backend" value={backend[0]} tone={backend[1]} />
        <StatusRow label="Database" value={database[0]} tone={database[1]} />
        {data?.schemaVersion && (
          <StatusRow label="Schema version" value={data.schemaVersion} tone="pending" />
        )}
      </dl>
    </section>
  )
}
