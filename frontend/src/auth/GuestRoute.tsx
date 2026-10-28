import { Navigate, Outlet, useLocation } from 'react-router'
import { FullPageSpinner } from '../components/FullPageSpinner.tsx'
import { useAuth } from './useAuth.ts'

/**
 * Login and register pages. Once the user is authenticated (by restoring a session or by
 * submitting the form) this is the single place that redirects them: back to the page they
 * originally asked for, or home.
 */
export function GuestRoute() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') return <FullPageSpinner />
  if (state.status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }
  return <Outlet />
}
