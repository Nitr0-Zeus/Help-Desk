import { ApiError } from '../api/client'

export type ProfileErrorKey =
  | 'profile.error.emailInUse'
  | 'profile.error.wrongCurrent'
  | 'profile.error.invalidNew'
  | 'profile.error.generic'

/**
 * Maps the API ProblemDetail contract to user copy:
 * 409 "Email already in use", 401 wrong current password, 422 validation.
 */
export function mapProfileError(error: unknown, fallback: ProfileErrorKey): ProfileErrorKey {
  if (!(error instanceof ApiError)) return fallback
  if (error.status === 409) return 'profile.error.emailInUse'
  if (error.status === 401) return 'profile.error.wrongCurrent'
  if (error.status === 422) return 'profile.error.invalidNew'
  return fallback
}
