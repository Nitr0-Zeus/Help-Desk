export type Priority = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA'

export type TicketStatus =
  | 'ABERTO'
  | 'EM_ATENDIMENTO'
  | 'AGUARDANDO_CLIENTE'
  | 'RESOLVIDO'
  | 'FECHADO'

export interface Ticket {
  id: number
  protocol: string
  title: string
  description: string
  priority: Priority
  status: TicketStatus
  createdById: number
  createdByName: string
  assignedToId: number | null
  assignedToName: string | null
  createdAt: string
  updatedAt: string
  firstResponseAt: string | null
  waitingSince: string | null
  resolvedAt: string | null
  closedAt: string | null
  firstResponseDueAt: string | null
  dueAt: string | null
  slaBreached: boolean
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TicketFilters {
  status: string
  priority: string
  slaBreached: string
  q: string
  page: number
  size: number
}

export interface CreateTicketPayload {
  title: string
  description: string
  priority: Priority
}
