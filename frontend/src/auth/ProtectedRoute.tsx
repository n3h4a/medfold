import { Navigate, Outlet, useLocation } from 'react-router'
import { FullPageSpinner } from '../components/FullPageSpinner.tsx'
import { useAuth } from './useAuth.ts'

/** Renders child routes only for logged-in users; others go to /login and come back after. */
export function ProtectedRoute() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') return <FullPageSpinner />
  if (state.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}
