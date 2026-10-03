import { apiFetch } from '../../api/client'
import type { AgentMetrics, GlobalMetrics } from '../types'

export function getMetrics(): Promise<GlobalMetrics> {
  return apiFetch<GlobalMetrics>('/metrics')
}

export function getAgentMetrics(): Promise<AgentMetrics[]> {
  return apiFetch<AgentMetrics[]>('/metrics/agents')
}
