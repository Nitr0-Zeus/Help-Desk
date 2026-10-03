import { apiFetch } from '../../api/client'
import type { CreateTicketPayload, Page, Ticket, TicketFilters, TicketStatus } from '../types'

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

export interface TimelineEntry {
  id: number
  type: 'COMMENT' | 'EVENT'
  eventType:
    | 'STATUS_CHANGED'
    | 'ASSIGNED'
    | 'SLA_PAUSED'
    | 'SLA_RESUMED'
    | 'SLA_BREACHED'
    | null
  authorId: number | null
  authorName: string | null
  body: string | null
  payload: string | null
  createdAt: string
}

export async function getTicket(id: number): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${id}`)
}

export async function transitionTicket(id: number, status: TicketStatus): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export async function assignTicket(id: number): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${id}/assign`, { method: 'POST', body: '{}' })
}

export async function unassignTicket(id: number): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${id}/assign`, { method: 'DELETE' })
}

export async function deleteTicket(id: number): Promise<void> {
  return apiFetch<void>(`/tickets/${id}`, { method: 'DELETE' })
}

export async function getTimeline(
  id: number,
  page = 0,
  size = 100,
): Promise<Page<TimelineEntry>> {
  return apiFetch<Page<TimelineEntry>>(`/tickets/${id}/timeline?page=${page}&size=${size}`)
}
