import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth.ts'
import { Logo, LogoutIcon, MoonIcon, SearchIcon, SunIcon } from '../components/icons.tsx'
import { CommandPalette } from '../features/search/CommandPalette.tsx'
import { navItems } from './nav.ts'
import { useTheme } from './theme.ts'

function initials(name: string | null, email: string): string {
  const source = name?.trim() || email
  const parts = source.split(/[\s@._-]+/).filter(Boolean)
  return (parts[0]?.[0] ?? '?').toUpperCase() + (name && parts[1] ? parts[1][0].toUpperCase() : '')
}

const iconButton =
  'rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500'

export function AppLayout() {
  const { state, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const [searchOpen, setSearchOpen] = useState(false)
  const location = useLocation()
  const authenticated = state.status === 'authenticated'

  // ⌘K / Ctrl+K opens search from anywhere.
  useEffect(() => {
    if (!authenticated) return
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setSearchOpen(true)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [authenticated])

  // Each page starts at the top.
  useEffect(() => {
    window.scrollTo?.(0, 0)
  }, [location.pathname])

  if (!authenticated) {
    return (
      <div className="min-h-screen">
        <Outlet />
      </div>
    )
  }

  const user = state.user
  return (
    <div className="min-h-screen lg:pl-64">
      <aside className="no-print sticky top-0 z-30 flex flex-wrap items-center border-b border-slate-200 bg-surface/90 backdrop-blur lg:fixed lg:inset-y-0 lg:left-0 lg:w-64 lg:flex-col lg:flex-nowrap lg:items-stretch lg:border-b-0 lg:border-r lg:bg-surface">
        <Link to="/" className="order-1 flex h-14 items-center gap-2.5 px-4 lg:h-16 lg:px-5" aria-label="Medfold home">
          <Logo />
          <span className="text-lg font-semibold tracking-tight text-slate-900">Medfold</span>
        </Link>

        <div className="order-2 ml-auto flex items-center gap-1 px-3 lg:order-3 lg:ml-0 lg:flex-col lg:items-stretch lg:gap-2 lg:border-t lg:border-slate-200 lg:p-4">
          <div className="flex items-center gap-1 lg:justify-between">
            <button type="button" onClick={() => setSearchOpen(true)} className={`${iconButton} lg:hidden`} aria-label="Search">
              <SearchIcon />
            </button>
            <Link
              to="/account"
              className="flex min-w-0 items-center gap-2.5 rounded-lg p-1 hover:bg-slate-100 lg:flex-1 lg:p-1.5"
              aria-label={`Your account (${user.email})`}
            >
              <span
                aria-hidden="true"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-400 to-brand-700 text-xs font-semibold text-white"
              >
                {initials(user.fullName, user.email)}
              </span>
              <span className="hidden min-w-0 lg:block">
                <span className="block truncate text-sm font-medium text-slate-900">{user.fullName ?? 'Your account'}</span>
                <span className="block truncate text-xs text-slate-500">{user.email}</span>
              </span>
            </Link>
            <button
              type="button"
              onClick={toggle}
              className={iconButton}
              aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
            </button>
          </div>
          <button
            type="button"
            onClick={() => void logout()}
            className="flex items-center gap-2 rounded-lg px-2 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 lg:px-3"
          >
            <LogoutIcon className="h-5 w-5 lg:h-4 lg:w-4" />
            <span className="sr-only lg:not-sr-only">Log out</span>
          </button>
        </div>

        <div className="order-3 w-full lg:order-2 lg:flex-1 lg:overflow-y-auto">
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            className="mx-4 mb-4 hidden w-[calc(100%-2rem)] items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500 hover:border-slate-300 hover:text-slate-700 lg:flex"
          >
            <SearchIcon className="h-4 w-4" />
            Search
            <kbd className="ml-auto rounded border border-slate-200 bg-surface px-1.5 font-sans text-[11px] text-slate-500">⌘K</kbd>
          </button>
          <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-3 pb-2 lg:flex-col lg:px-3 lg:pb-0">
            {navItems.map(({ to, label, icon: ItemIcon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-brand-50 text-brand-ink'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                <ItemIcon className="hidden h-5 w-5 lg:block" />
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
      </aside>

      <main className="print-plain mx-auto max-w-5xl px-4 py-8 lg:px-8 lg:py-10">
        <Outlet />
      </main>
      <footer className="no-print mx-auto max-w-5xl px-4 pb-8 text-center text-xs text-slate-500 lg:px-8">
        Medfold organizes your records. It does not give medical advice. ·{' '}
        <Link to="/status" className="hover:underline">
          System status
        </Link>
      </footer>
      {searchOpen && <CommandPalette onClose={() => setSearchOpen(false)} />}
    </div>
  )
}
