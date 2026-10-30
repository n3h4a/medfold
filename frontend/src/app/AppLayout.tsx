import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth.ts'

export function AppLayout() {
  const { state, logout } = useAuth()

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-4 px-4">
          <Link to="/" className="text-lg font-semibold text-brand-700">
            Medfold
          </Link>
          {state.status === 'authenticated' && (
            <div className="flex min-w-0 items-center gap-3">
              <span className="hidden truncate text-sm text-slate-600 sm:inline">{state.user.email}</span>
              <button
                type="button"
                onClick={() => void logout()}
                className="shrink-0 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-500"
              >
                Log out
              </button>
            </div>
          )}
        </div>
        {state.status === 'authenticated' && (
          <nav aria-label="Main" className="mx-auto flex max-w-5xl gap-1 overflow-x-auto px-4 pb-2">
            {[
              { to: '/', label: 'Documents', end: true },
              { to: '/labs', label: 'Test results', end: false },
              { to: '/medications', label: 'Medicines', end: false },
            ].map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `shrink-0 rounded-lg px-3 py-1.5 text-sm font-medium ${
                    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
      {state.status === 'authenticated' && (
        <footer className="mx-auto max-w-5xl px-4 pb-8 text-center text-xs text-slate-500">
          Medfold organizes your records. It does not give medical advice. ·{' '}
          <Link to="/status" className="hover:underline">
            System status
          </Link>
        </footer>
      )}
    </div>
  )
}
