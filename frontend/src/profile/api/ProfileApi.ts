import { apiFetch } from '../../api/client'
import type { Role } from '../../auth/auth-types'

export interface UserProfile {
  id: number
  name: string
  email: string
  role: Role
  active: boolean
}

export function getProfile(): Promise<UserProfile> {
  return apiFetch<UserProfile>('/users/me')
}

export function updateProfile(patch: { name?: string; email?: string }): Promise<UserProfile> {
  return apiFetch<UserProfile>('/users/me', {
    method: 'PATCH',
    body: JSON.stringify(patch),
  })
}

export function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  return apiFetch<void>('/users/me/password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

export function deactivateAccount(): Promise<void> {
  return apiFetch<void>('/users/me', { method: 'DELETE' })
}
