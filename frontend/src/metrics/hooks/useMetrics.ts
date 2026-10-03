import { useQuery } from '@tanstack/react-query'
import { getAgentMetrics, getMetrics } from '../api/MetricsApi'

export function useMetrics() {
  return useQuery({
    queryKey: ['metrics'],
    queryFn: getMetrics,
  })
}

export function useAgentMetrics() {
  return useQuery({
    queryKey: ['metrics', 'agents'],
    queryFn: getAgentMetrics,
  })
}
