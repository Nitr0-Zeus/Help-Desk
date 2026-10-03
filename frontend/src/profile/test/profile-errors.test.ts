import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/client'
import { mapProfileError } from '../profile-errors'

describe('mapProfileError', () => {
  it('maps 409 to the duplicate-email copy', () => {
    const error = new ApiError(409, { status: 409, detail: 'Email already in use' })
    expect(mapProfileError(error, 'profile.error.generic')).toBe('profile.error.emailInUse')
  })

  it('maps 401 to the wrong-current-password copy', () => {
    const error = new ApiError(401, { status: 401, detail: 'Current password is invalid' })
    expect(mapProfileError(error, 'profile.error.generic')).toBe('profile.error.wrongCurrent')
  })

  it('maps 422 to the invalid-password copy', () => {
    const error = new ApiError(422, { status: 422, violations: { newPassword: 'too short' } })
    expect(mapProfileError(error, 'profile.error.generic')).toBe('profile.error.invalidNew')
  })

  it('falls back for anything else, including non-Api errors', () => {
    expect(mapProfileError(new Error('boom'), 'profile.error.generic')).toBe('profile.error.generic')
    expect(mapProfileError(new ApiError(500, { status: 500 }), 'profile.error.generic')).toBe(
      'profile.error.generic',
    )
  })
})
