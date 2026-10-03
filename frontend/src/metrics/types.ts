import type { TicketStatus } from '../tickets/types'

export interface GlobalMetrics {
  total: number
  byStatus: Record<TicketStatus, number>
  slaCompliancePercent: number | null
  avgFirstResponseHours: number | null
  avgResolutionHours: number | null
}

export interface AgentMetrics {
  agentId: number
  agentName: string
  assigned: number
  resolved: number
  slaCompliancePercent: number | null
  avgFirstResponseHours: number | null
  avgResolutionHours: number | null
}
