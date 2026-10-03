package com.helpdesk.metrics;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.metrics.dto.MetricsDtos.AgentMetrics;
import com.helpdesk.metrics.dto.MetricsDtos.GlobalMetrics;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketStatus;
import com.helpdesk.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

@Service
public class MetricsService {

    private final TicketRepository tickets;
    private final UserService userService;

    public MetricsService(TicketRepository tickets, UserService userService) {
        this.tickets = tickets;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public GlobalMetrics global() {
        List<Ticket> all = tickets.findAll();
        Map<TicketStatus, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            byStatus.put(status, all.stream().filter(t -> t.getStatus() == status).count());
        }
        return new GlobalMetrics(all.size(), byStatus,
                compliance(all),
                avgHours(all, Ticket::getCreatedAt, Ticket::getFirstResponseAt),
                avgHours(all, Ticket::getCreatedAt, Ticket::getResolvedAt));
    }

    @Transactional(readOnly = true)
    public List<AgentMetrics> perAgent(List<User> agents) {
        return agents.stream().map(this::metricsFor).toList();
    }

    @Transactional(readOnly = true)
    public AgentMetrics forAgent(Long agentId) {
        User user = userService.loadRequired(agentId);
        if (user.getRole() != Role.AGENTE) {
            throw new NotFoundException("Agent not found");
        }
        return metricsFor(user);
    }

    private AgentMetrics metricsFor(User agent) {
        List<Ticket> assigned = tickets.findAll().stream()
                .filter(t -> t.getAssignedTo() != null
                        && t.getAssignedTo().getId().equals(agent.getId()))
                .toList();
        long resolved = assigned.stream()
                .filter(t -> t.getResolvedAt() != null).count();
        return new AgentMetrics(agent.getId(), agent.getName(), assigned.size(), resolved,
                compliance(assigned),
                avgHours(assigned, Ticket::getCreatedAt, Ticket::getFirstResponseAt),
                avgHours(assigned, Ticket::getCreatedAt, Ticket::getResolvedAt));
    }

    private Double compliance(List<Ticket> tickets) {
        // resolvedAt, not status: a ticket resolved then closed still counts
        List<Ticket> resolved = tickets.stream()
                .filter(t -> t.getResolvedAt() != null && t.getDueAt() != null)
                .toList();
        if (resolved.isEmpty()) {
            return null;
        }
        long within = resolved.stream()
                .filter(t -> !t.getResolvedAt().isAfter(t.getDueAt())).count();
        return within * 100.0 / resolved.size();
    }

    private Double avgHours(List<Ticket> tickets, Function<Ticket, Instant> from,
                            Function<Ticket, Instant> to) {
        OptionalDouble avg = tickets.stream()
                .filter(t -> from.apply(t) != null && to.apply(t) != null)
                .mapToDouble(t -> Duration.between(from.apply(t), to.apply(t)).toMinutes() / 60.0)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 100.0) / 100.0 : null;
    }
}
