import { useQuery } from '@tanstack/react-query'
import { listTickets } from '../api/TicketsApi'
import type { TicketFilters } from '../types'

export function useTickets(filters: TicketFilters) {
  // URL params are part of the query key: the URL is the source of truth (spec decision 7)
  return useQuery({
    queryKey: ['tickets', filters],
    queryFn: () => listTickets(filters),
  })
}
