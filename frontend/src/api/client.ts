import { tokenStorage } from '../auth/token-storage'
import type { AuthResponse } from '../auth/auth-types'

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  violations?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

const BASE = '/api'

/**
 * Single-flight refresh (spec decision 2): concurrent 401s share one
 * /auth/refresh promise — never multiple refreshes, never a retry loop.
 */
let refreshInFlight: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefresh()
  if (!refreshToken) return null
  try {
    const response = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) return null
    const data = (await response.json()) as AuthResponse
    tokenStorage.set(data.accessToken, data.refreshToken)
    return data.accessToken
  } catch {
    return null
  }
}

function sharedRefresh(): Promise<string | null> {
  refreshInFlight ??= refreshAccessToken().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

function endSession(): void {
  tokenStorage.clear()
  window.dispatchEvent(new Event('auth:logout'))
}

async function authenticatedFetch(path: string, init: RequestInit, retried = false): Promise<Response> {
  const headers = new Headers(init.headers)
  const access = tokenStorage.getAccess()
  if (access) headers.set('Authorization', `Bearer ${access}`)
  const isFormData = init.body instanceof FormData
  if (init.body && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${BASE}${path}`, { ...init, headers })

  if (response.status === 401 && !retried && !path.startsWith('/auth/')) {
    const renewed = await sharedRefresh()
    if (renewed) {
      return authenticatedFetch(path, init, true)
    }
    endSession()
    throw new ApiError(401, { status: 401, detail: 'Session expired' })
  }
  return response
}

/** Binary download with the same auth/refresh rules; never a public URL. */
export async function apiBlob(path: string): Promise<Blob> {
  const response = await authenticatedFetch(path, {})
  if (!response.ok) {
    throw new ApiError(response.status, { status: response.status })
  }
  return response.blob()
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  retried = false,
): Promise<T> {
  const requestInit: RequestInit = {
    ...init,
    headers: { Accept: 'application/json', ...init.headers },
  }
  const response = await authenticatedFetch(path, requestInit, retried)

  if (!response.ok) {
    let problem: ProblemDetail
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      problem = { status: response.status, detail: response.statusText }
    }
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
