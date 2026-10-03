package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SlaCalculator {

    public record Policy(int firstResponseHours, int resolutionHours) {
    }

    public record SlaDeadlines(Instant firstResponseDueAt, Instant dueAt) {
    }

    public record TicketSlaView(Instant firstResponseDueAt, Instant dueAt,
                                Instant firstResponseAt, Instant resolvedAt,
                                Instant closedAt, boolean paused) {
    }

    private final BusinessCalendar calendar;
    private final Map<Priority, Policy> policies;

    @org.springframework.beans.factory.annotation.Autowired
    public SlaCalculator(BusinessCalendar calendar, SlaPolicyRepository policyRepository) {
        this(calendar, Map.of(
                Priority.BAIXA, policyFrom(policyRepository, Priority.BAIXA),
                Priority.MEDIA, policyFrom(policyRepository, Priority.MEDIA),
                Priority.ALTA, policyFrom(policyRepository, Priority.ALTA),
                Priority.CRITICA, policyFrom(policyRepository, Priority.CRITICA)));
    }

    SlaCalculator(BusinessCalendar calendar, Map<Priority, Policy> policies) {
        this.calendar = calendar;
        this.policies = policies;
    }

    private static Policy policyFrom(SlaPolicyRepository repo, Priority priority) {
        SlaPolicy policy = repo.findByPriority(priority)
                .orElseThrow(() -> new IllegalStateException("Missing SLA policy for " + priority));
        return new Policy(policy.getFirstResponseHours(), policy.getResolutionHours());
    }

    public SlaDeadlines calculate(Priority priority, Instant from) {
        Policy policy = policies.get(priority);
        return new SlaDeadlines(
                calendar.addWorkingHours(from, policy.firstResponseHours()),
                calendar.addWorkingHours(from, policy.resolutionHours()));
    }

    public Instant shift(Instant dueAt, long minutesToShift) {
        return calendar.addWorkingMinutes(dueAt, minutesToShift);
    }

    public boolean isBreached(TicketSlaView view, Instant now) {
        if (view.resolvedAt() != null || view.closedAt() != null) {
            return false;
        }
        boolean pastFirstResponse = view.firstResponseAt() == null
                && view.firstResponseDueAt() != null && now.isAfter(view.firstResponseDueAt());
        boolean pastResolution = view.dueAt() != null && now.isAfter(view.dueAt());
        return pastFirstResponse || pastResolution;
    }
}
