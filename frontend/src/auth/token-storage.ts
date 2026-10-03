const ACCESS_KEY = 'hd.access'
const REFRESH_KEY = 'hd.refresh'

/**
 * sessionStorage only — never localStorage (design spec, decision 1).
 * Survives reload, cleared when the tab session ends.
 */
export const tokenStorage = {
  getAccess(): string | null {
    return sessionStorage.getItem(ACCESS_KEY)
  },
  getRefresh(): string | null {
    return sessionStorage.getItem(REFRESH_KEY)
  },
  set(access: string, refresh: string): void {
    sessionStorage.setItem(ACCESS_KEY, access)
    sessionStorage.setItem(REFRESH_KEY, refresh)
  },
  setAccess(access: string): void {
    sessionStorage.setItem(ACCESS_KEY, access)
  },
  clear(): void {
    sessionStorage.removeItem(ACCESS_KEY)
    sessionStorage.removeItem(REFRESH_KEY)
  },
}
