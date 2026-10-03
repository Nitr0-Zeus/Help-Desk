export type Role = 'CLIENTE' | 'AGENTE'

export interface SessionUser {
  id: string
  email: string
  role: Role
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  refreshExpiresAt: string
}

export interface RegisterPayload {
  name: string
  email: string
  password: string
  role: Role
  inviteCode?: string
}
