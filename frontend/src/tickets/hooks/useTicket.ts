import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  assignTicket,
  deleteTicket,
  getTicket,
  getTimeline,
  transitionTicket,
  unassignTicket,
} from '../api/TicketsApi'
import type { TicketStatus } from '../types'

export function useTicket(id: number) {
  return useQuery({
    queryKey: ['ticket', id],
    queryFn: () => getTicket(id),
    enabled: Number.isFinite(id),
  })
}

export function useTimeline(id: number) {
  return useQuery({
    queryKey: ['timeline', id],
    queryFn: () => getTimeline(id),
    enabled: Number.isFinite(id),
  })
}

function useInvalidateTicket(id: number) {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['ticket', id] })
    queryClient.invalidateQueries({ queryKey: ['timeline', id] })
    queryClient.invalidateQueries({ queryKey: ['tickets'] })
  }
}

export function useTransition(id: number) {
  const invalidate = useInvalidateTicket(id)
  return useMutation({
    mutationFn: (status: TicketStatus) => transitionTicket(id, status),
    onSuccess: invalidate,
  })
}

export function useAssign(id: number) {
  const invalidate = useInvalidateTicket(id)
  return useMutation({
    mutationFn: (release: boolean) => (release ? unassignTicket(id) : assignTicket(id)),
    onSuccess: invalidate,
  })
}

export function useDeleteTicket(id: number) {
  const invalidate = useInvalidateTicket(id)
  return useMutation({
    mutationFn: () => deleteTicket(id),
    onSuccess: invalidate,
  })
}
