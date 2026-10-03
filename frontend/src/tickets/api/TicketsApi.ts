import { apiFetch } from '../../api/client'
import type { CreateTicketPayload, Page, Ticket, TicketFilters } from '../types'

export async function listTickets(filters: TicketFilters): Promise<Page<Ticket>> {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.slaBreached) params.set('slaBreached', filters.slaBreached)
  if (filters.q) params.set('q', filters.q)
  params.set('page', String(filters.page))
  params.set('size', String(filters.size))
  params.set('sort', 'updatedAt,desc')
  return apiFetch<Page<Ticket>>(`/tickets?${params.toString()}`)
}

export async function createTicket(payload: CreateTicketPayload): Promise<Ticket> {
  return apiFetch<Ticket>('/tickets', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
