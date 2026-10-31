import type { ReactNode } from 'react'
import { CheckIcon, Logo } from '../components/icons.tsx'

const promises = [
  'Upload a PDF or a phone photo. Medfold reads it for you.',
  'Every test value lines up on one chart, whichever lab printed it.',
  'One printable summary for your next appointment.',
  'Your data stays yours: export or delete it any time.',
]

/** The paper-folder problem, drawn: scattered reports becoming one tidy timeline. */
function Illustration() {
  return (
    <svg aria-hidden="true" viewBox="0 0 320 180" className="w-full max-w-sm">
      <g opacity="0.9">
        <rect x="18" y="38" width="62" height="80" rx="6" fill="#fff" opacity="0.18" transform="rotate(-12 49 78)" />
        <rect x="40" y="58" width="62" height="80" rx="6" fill="#fff" opacity="0.28" transform="rotate(8 71 98)" />
        <rect x="30" y="50" width="62" height="80" rx="6" fill="#fff" opacity="0.92" />
        <path d="M40 66h40M40 76h30M40 86h36M40 96h24" stroke="#1f726b" strokeWidth="3" strokeLinecap="round" opacity="0.5" />
      </g>
      <path d="M112 90h40" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeDasharray="2 8" />
      <path d="m146 82 9 8-9 8" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="170" y="28" width="130" height="124" rx="12" fill="#fff" />
      <path d="M186 118 212 96l22 12 26-34 24 10" fill="none" stroke="#2a8c84" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="186" y="62" width="98" height="30" rx="4" fill="#2a8c84" opacity="0.1" />
      {[186, 212, 234, 260, 284].map((x, i) => (
        <circle key={x} cx={x} cy={[118, 96, 108, 74, 84][i]} r="5" fill="#fff" stroke="#2a8c84" strokeWidth="3" />
      ))}
      <path d="M186 42h50" stroke="#94a3b8" strokeWidth="4" strokeLinecap="round" />
      <path d="M186 136h80" stroke="#cbd5e1" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

export function AuthCard({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden bg-gradient-to-br from-brand-500 via-brand-600 to-brand-700 p-12 text-white lg:flex lg:flex-col">
        <div aria-hidden="true" className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-white/10 blur-2xl" />
        <div aria-hidden="true" className="absolute -bottom-32 -left-16 h-80 w-80 rounded-full bg-brand-400/30 blur-3xl" />
        <div className="relative flex items-center gap-2.5">
          <Logo className="h-9 w-9" />
          <span className="text-xl font-semibold tracking-tight">Medfold</span>
        </div>
        <div className="relative mt-auto space-y-8">
          <Illustration />
          <div>
            <p className="text-3xl font-semibold leading-tight tracking-tight">
              Your medical history,
              <br />
              finally in one place.
            </p>
            <p className="mt-3 max-w-md text-brand-100">
              No more carrying a folder of paper reports from doctor to doctor.
            </p>
          </div>
          <ul className="space-y-2.5">
            {promises.map((promise) => (
              <li key={promise} className="flex items-start gap-3 text-sm text-brand-50">
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/20">
                  <CheckIcon className="h-3.5 w-3.5" />
                </span>
                {promise}
              </li>
            ))}
          </ul>
        </div>
      </aside>
      <div className="flex flex-col justify-center px-4 py-12 sm:px-8">
        <div className="mx-auto w-full max-w-md animate-rise">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <Logo />
            <span className="text-lg font-semibold tracking-tight text-slate-900">Medfold</span>
          </div>
          <h1 className="text-3xl font-semibold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-2 text-slate-600">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <p className="mt-10 text-xs text-slate-400">Medfold organizes your records. It does not give medical advice.</p>
        </div>
      </div>
    </div>
  )
}

export function FormError({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
      {message}
    </div>
  )
}

export function SubmitButton({ pending, children }: { pending: boolean; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={pending}
      className="w-full rounded-lg bg-brand-600 px-4 py-2.5 font-medium text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {children}
    </button>
  )
}
