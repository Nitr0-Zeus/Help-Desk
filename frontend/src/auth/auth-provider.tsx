import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as authClient from './auth-client'
import { tokenStorage } from './token-storage'
import type { AuthResponse, RegisterPayload, Role, SessionUser } from './auth-types'

interface AuthValue {
  user: SessionUser | null
  status: 'authed' | 'anon'
  login: (email: string, password: string) => Promise<AuthResponse>
  register: (payload: RegisterPayload) => Promise<AuthResponse>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

/**
 * Identity for UX only (spec decision 4): the decoded JWT drives what the UI
 * shows; every authorization decision stays on the backend. Renewal of expired
 * access tokens happens transparently in api/client on the first 401.
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
  const queryClient = useQueryClient()
  const [user, setUser] = useState<SessionUser | null>(() => decodeSession(tokenStorage.getAccess()))

  const clearSession = useCallback(() => {
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  // api/client signals an unrecoverable refresh failure
  useEffect(() => {
    const onExpired = () => clearSession()
    window.addEventListener('auth:logout', onExpired)
    return () => window.removeEventListener('auth:logout', onExpired)
  }, [clearSession])

  const login = useCallback(async (email: string, password: string) => {
    const response = await authClient.login(email, password)
    setUser(decodeSession(response.accessToken))
    return response
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    const response = await authClient.register(payload)
    setUser(decodeSession(response.accessToken))
    return response
  }, [])

  const logout = useCallback(async () => {
    await authClient.logout()
    clearSession()
  }, [clearSession])

  const value = useMemo<AuthValue>(
    () => ({ user, status: user ? 'authed' : 'anon', login, register, logout }),
    [user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
