import { SystemStatus } from '../status/SystemStatus.tsx'

export function HomePage() {
  return (
    <div className="grid gap-8 md:grid-cols-[1fr_320px]">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight text-slate-900">
          Upload once. Find everything.
        </h1>
        <p className="mt-3 max-w-prose text-slate-600">
          Medfold keeps your lab reports, prescriptions and discharge summaries in one
          private, searchable timeline, so you can understand your own medical history.
        </p>
      </div>
      <SystemStatus />
    </div>
  )
}
