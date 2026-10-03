import { apiFetch } from '../api/client'
import { tokenStorage } from './token-storage'
import type { AuthResponse, RegisterPayload } from './auth-types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  const data = await apiFetch<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  tokenStorage.set(data.accessToken, data.refreshToken)
  return data
}

export async function register(payload: RegisterPayload): Promise<AuthResponse> {
  const data = await apiFetch<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  tokenStorage.set(data.accessToken, data.refreshToken)
  return data
}

/** Clears the local session no matter how the endpoint answers (spec decision 3). */
export async function logout(): Promise<void> {
  const refreshToken = tokenStorage.getRefresh()
  tokenStorage.clear()
  if (!refreshToken) return
  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
  } catch {
    // local session is already gone; server-side revocation is best effort
  }
}
