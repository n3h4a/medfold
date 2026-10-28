import { useAuth } from '../../auth/useAuth.ts'
import { SystemStatus } from '../status/SystemStatus.tsx'

export function HomePage() {
  const { state } = useAuth()
  const name = state.status === 'authenticated' ? (state.user.fullName ?? state.user.email) : ''

  return (
    <div className="grid gap-8 md:grid-cols-[1fr_320px]">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight text-slate-900">Welcome, {name}</h1>
        <p className="mt-3 max-w-prose text-slate-600">
          Your medical timeline will appear here once you upload your first document.
        </p>
      </div>
      <SystemStatus />
    </div>
  )
}
