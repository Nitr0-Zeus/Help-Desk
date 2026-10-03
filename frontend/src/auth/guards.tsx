import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './auth-provider'
import type { Role } from './auth-types'

/** Login and register are only reachable when there is no session. */
export function PublicRoute() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'authed') {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/tickets'} replace />
  }
  return <Outlet />
}

/** Every app route requires a session; the API stays the authorization authority. */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'anon') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/**
 * Role guard for UX-level visibility (spec decision 6). The backend enforces
 * the real permission; no current route needs it yet — kept exported and tested.
 */
export function RequireRole({ role, children }: { role: Role; children: React.ReactNode }) {
  const { user } = useAuth()
  if (!user || user.role !== role) {
    return <Navigate to="/tickets" replace />
  }
  return <>{children}</>
}
