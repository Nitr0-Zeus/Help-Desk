import { createContext, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { tokenStorage } from './token-storage'
import type { Role, SessionUser } from './auth-types'

interface AuthValue {
  user: SessionUser | null
  status: 'authed' | 'anon'
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

/**
 * Identity for UX only (spec decision 4): the decoded JWT drives what the UI
 * shows; every authorization decision stays on the backend.
 * Task 4 replaces the `logout` stub with the full auth-client flow.
 */
function decodeSession(token: string | null): SessionUser | null {
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    if (typeof json.sub !== 'string' || typeof json.email !== 'string') return null
    return { id: json.sub, email: json.email, role: json.role as Role }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(() => decodeSession(tokenStorage.getAccess()))

  const value = useMemo<AuthValue>(
    () => ({
      user,
      status: user ? 'authed' : 'anon',
      logout: () => {
        tokenStorage.clear()
        setUser(null)
      },
    }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
