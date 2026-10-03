package com.helpdesk.metrics.dto;

import com.helpdesk.ticket.TicketStatus;

import java.util.Map;

public final class MetricsDtos {

    private MetricsDtos() {
    }

    public record GlobalMetrics(long total, Map<TicketStatus, Long> byStatus,
                                Double slaCompliancePercent,
                                Double avgFirstResponseHours,
                                Double avgResolutionHours) {
    }

    public record AgentMetrics(Long agentId, String agentName, long assigned, long resolved,
                               Double slaCompliancePercent,
                               Double avgFirstResponseHours,
                               Double avgResolutionHours) {
    }
}
