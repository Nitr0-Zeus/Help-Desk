package com.helpdesk.ticket;

import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.security.UserContext;
import com.helpdesk.sla.BusinessCalendar;
import com.helpdesk.sla.SlaCalculator;
import com.helpdesk.ticket.dto.TimelineDtos.TimelineEntryResponse;
import com.helpdesk.ticket.dto.TimelineDtos.TimelinePage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TimelineService {

    private final TimelineRepository timeline;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final SlaCalculator slaCalculator;
    private final BusinessCalendar businessCalendar;
    private final TimeProvider time;

    public TimelineService(TimelineRepository timeline, TicketRepository tickets,
                           UserRepository users, SlaCalculator slaCalculator,
                           BusinessCalendar businessCalendar, TimeProvider time) {
        this.timeline = timeline;
        this.tickets = tickets;
        this.users = users;
        this.slaCalculator = slaCalculator;
        this.businessCalendar = businessCalendar;
        this.time = time;
    }

    @Transactional
    public TimelineEntryResponse addComment(Long ticketId, UserContext actor, String body) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        User author = users.findById(actor.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        Instant now = time.now();

        TimelineEntry entry = timeline.save(TimelineEntry.comment(ticket, author, body.trim(), now));

        if (actor.isAgent() && ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(now);
        }
        if (!actor.isAgent() && ticket.getStatus() == TicketStatus.AGUARDANDO_CLIENTE) {
            resume(ticket, author, now);
        }
        tickets.save(ticket);
        recomputeBreach(ticket, now);
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public TimelinePage timeline(Long ticketId, Pageable pageable) {
        Page<TimelineEntry> page = timeline.findByTicketId(ticketId, pageable);
        return new TimelinePage(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public void appendEvent(Ticket ticket, EventType type, UserContext actor, String payload,
                            Instant now) {
        User author = actor == null ? null : users.findById(actor.id()).orElse(null);
        timeline.save(TimelineEntry.event(ticket, type, author, payload, now));
    }

    /**
     * Recomputes the SLA breach flag on demand (on read and on transitions).
     * The SLA_BREACHED audit event is written at most once per ticket.
     */
    @Transactional
    public void recomputeBreach(Ticket ticket, Instant now) {
        var view = new SlaCalculator.TicketSlaView(
                ticket.getFirstResponseDueAt(), ticket.getDueAt(),
                ticket.getFirstResponseAt(), ticket.getResolvedAt(),
                ticket.getClosedAt(), ticket.getWaitingSince() != null);
        if (slaCalculator.isBreached(view, now) && !ticket.isSlaBreached()) {
            ticket.setSlaBreached(true);
            tickets.save(ticket);
            if (!timeline.existsByTicketIdAndEventType(ticket.getId(), EventType.SLA_BREACHED)) {
                timeline.save(TimelineEntry.event(ticket, EventType.SLA_BREACHED, null, "{}", now));
            }
        }
    }

    private void resume(Ticket ticket, User actor, Instant now) {
        long pausedMinutes = ticket.getWaitingSince() == null ? 0
                : businessCalendar.workingMinutesBetween(ticket.getWaitingSince(), now);
        if (pausedMinutes > 0) {
            ticket.setFirstResponseDueAt(slaCalculator.shift(ticket.getFirstResponseDueAt(), pausedMinutes));
            ticket.setDueAt(slaCalculator.shift(ticket.getDueAt(), pausedMinutes));
            timeline.save(TimelineEntry.event(ticket, EventType.SLA_RESUMED, actor,
                    "{\"pausedMinutes\":" + pausedMinutes + "}", now));
        }
        TicketStatus from = ticket.getStatus();
        ticket.setWaitingSince(null);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        ticket.touch(now);
        tickets.save(ticket);
        timeline.save(TimelineEntry.event(ticket, EventType.STATUS_CHANGED, actor,
                "{\"from\":\"" + from + "\",\"to\":\"" + ticket.getStatus() + "\"}", now));
    }

    private TimelineEntryResponse toResponse(TimelineEntry entry) {
        return new TimelineEntryResponse(
                entry.getId(), entry.getType(), entry.getEventType(),
                entry.getAuthor() == null ? null : entry.getAuthor().getId(),
                entry.getAuthor() == null ? null : entry.getAuthor().getName(),
                entry.getBody(), entry.getPayload(), entry.getCreatedAt());
    }
}
