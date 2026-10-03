import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from './client'
import { tokenStorage } from '../auth/token-storage'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const authResponse = {
  accessToken: 'renewed-access',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'renewed-refresh',
  refreshExpiresAt: new Date().toISOString(),
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  sessionStorage.clear()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  sessionStorage.clear()
})

describe('apiFetch auth flow', () => {
  it('on 401 refreshes once and retries the original request once', async () => {
    tokenStorage.set('old-access', 'old-refresh')
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, detail: 'expired' }))
      .mockResolvedValueOnce(jsonResponse(200, authResponse))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

    const result = await apiFetch<{ ok: boolean }>('/tickets')

    expect(result).toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const retryHeaders = new Headers(fetchMock.mock.calls[2][1].headers)
    expect(retryHeaders.get('Authorization')).toBe('Bearer renewed-access')
    expect(tokenStorage.getRefresh()).toBe('renewed-refresh')
  })

  it('collapses concurrent 401s into a single refresh call', async () => {
    tokenStorage.set('old-access', 'old-refresh')
    let refreshCalls = 0
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (String(url).endsWith('/auth/refresh')) {
        refreshCalls += 1
        return jsonResponse(200, authResponse)
      }
      const auth = new Headers(init.headers).get('Authorization')
      if (auth === 'Bearer old-access') {
        return jsonResponse(401, { status: 401, detail: 'expired' })
      }
      return jsonResponse(200, { path: String(url) })
    })

    const [a, b] = await Promise.all([apiFetch('/tickets'), apiFetch('/metrics')])

    expect(a).toEqual({ path: '/api/tickets' })
    expect(b).toEqual({ path: '/api/metrics' })
    expect(refreshCalls).toBe(1)
  })

  it('never retries more than once even when the retry is a 401', async () => {
    tokenStorage.set('old-access', 'old-refresh')
    let refreshCalls = 0
    fetchMock.mockImplementation(async (url: string) => {
      if (String(url).endsWith('/auth/refresh')) {
        refreshCalls += 1
        return jsonResponse(200, authResponse)
      }
      return jsonResponse(401, { status: 401, detail: 'still invalid' })
    })

    await expect(apiFetch('/tickets')).rejects.toBeInstanceOf(ApiError)
    expect(refreshCalls).toBe(1)
    // original + refresh + retry = 3, no fourth call
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('clears the session and fires auth:logout when refresh fails', async () => {
    tokenStorage.set('old-access', 'old-refresh')
    const onLogout = vi.fn()
    window.addEventListener('auth:logout', onLogout)
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, detail: 'bad refresh' }))

    const error = await apiFetch('/tickets').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(tokenStorage.getAccess()).toBeNull()
    expect(tokenStorage.getRefresh()).toBeNull()
    expect(onLogout).toHaveBeenCalledTimes(1)
    window.removeEventListener('auth:logout', onLogout)
  })

  it('does not attempt a refresh for /auth endpoints', async () => {
    tokenStorage.set('old-access', 'old-refresh')
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { status: 401, detail: 'Invalid credentials' }))

    const error = await apiFetch('/auth/login', { method: 'POST' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    // session untouched on failed login
    expect(tokenStorage.getAccess()).toBe('old-access')
  })
})
