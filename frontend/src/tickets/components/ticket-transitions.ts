import type { Role } from '../../auth/auth-types'
import type { TicketStatus } from '../types'

/**
 * Matrix copied from the backend contract (TicketStatus.canTransitionTo):
 * the UI shows only actions the API would accept — but the API stays the
 * authority (a bypassed button still gets a 403/409 from the server).
 */
const ALLOWED: Record<TicketStatus, TicketStatus[]> = {
  ABERTO: ['EM_ATENDIMENTO'],
  EM_ATENDIMENTO: ['AGUARDANDO_CLIENTE', 'RESOLVIDO'],
  AGUARDANDO_CLIENTE: ['EM_ATENDIMENTO'],
  RESOLVIDO: ['FECHADO'],
  FECHADO: [],
}

export function availableTransitions(
  status: TicketStatus,
  role: Role,
  isOwner: boolean,
): TicketStatus[] {
  if (role === 'AGENTE') return ALLOWED[status]
  // clients may only close their own resolved ticket (backend rule)
  if (status === 'RESOLVIDO' && isOwner) return ['FECHADO']
  return []
}

/** Assign/release is agent-only and only while the ticket is open-ish. */
export function canAssign(status: TicketStatus, role: Role): boolean {
  return role === 'AGENTE' && (status === 'ABERTO' || status === 'EM_ATENDIMENTO')
}

/** Deletion: owner only, and only while ABERTO (backend rule). */
export function canDelete(status: TicketStatus, role: Role, isOwner: boolean): boolean {
  return isOwner && role !== 'AGENTE' && status === 'ABERTO'
}
