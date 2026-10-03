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
import java.util.List;
import java.util.Map;

@Service
public class TimelineService {

    private final TimelineRepository timeline;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final SlaCalculator slaCalculator;
    private final BusinessCalendar businessCalendar;
    private final TimeProvider time;
    private final com.helpdesk.attachment.AttachmentService attachmentService;

    public TimelineService(TimelineRepository timeline, TicketRepository tickets,
                           UserRepository users, SlaCalculator slaCalculator,
                           BusinessCalendar businessCalendar, TimeProvider time,
                           com.helpdesk.attachment.AttachmentService attachmentService) {
        this.timeline = timeline;
        this.tickets = tickets;
        this.users = users;
        this.slaCalculator = slaCalculator;
        this.businessCalendar = businessCalendar;
        this.time = time;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public com.helpdesk.ticket.dto.TimelineDtos.CommentResponse addComment(Long ticketId,
                                                                           UserContext actor,
                                                                           String body,
                                                                           org.springframework.web.multipart.MultipartFile[] files) {
        if (body == null || body.isBlank()) {
            throw new com.helpdesk.common.error.UnprocessableException("Comment body must not be blank");
        }
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        User author = users.findById(actor.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        Instant now = time.now();

        TimelineEntry entry = timeline.save(TimelineEntry.comment(ticket, author, body.trim(), now));
        var savedAttachments = attachmentService.attach(entry, files);

        if (actor.isAgent() && ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(now);
        }
        if (!actor.isAgent() && ticket.getStatus() == TicketStatus.AGUARDANDO_CLIENTE) {
            resume(ticket, author, now);
        }
        tickets.save(ticket);
        recomputeBreach(ticket, now);

        var response = toResponse(entry, savedAttachments.stream()
                .map(a -> new com.helpdesk.ticket.dto.TimelineDtos.AttachmentView(
                        a.getId(), a.getOriginalFilename(), a.getContentType(), a.getSize()))
                .toList());
        return new com.helpdesk.ticket.dto.TimelineDtos.CommentResponse(
                response.id(), response.type(), response.eventType(),
                response.authorId(), response.authorName(), response.body(),
                response.payload(), response.createdAt(),
                response.attachments());
    }

    @Transactional(readOnly = true)
    public TimelinePage timeline(Long ticketId, Pageable pageable) {
        Page<TimelineEntry> page = timeline.findByTicketId(ticketId, pageable);
        var entryIds = page.getContent().stream().map(TimelineEntry::getId).toList();
        var attachments = attachmentService.findByTimelineEntryIds(entryIds);
        var byEntry = new java.util.HashMap<Long, java.util.List<com.helpdesk.ticket.dto.TimelineDtos.AttachmentView>>();
        attachments.stream()
                .map(a -> Map.entry(a.getTimelineEntry().getId(),
                        new com.helpdesk.ticket.dto.TimelineDtos.AttachmentView(
                                a.getId(), a.getOriginalFilename(), a.getContentType(), a.getSize())))
                .forEach(e -> byEntry.computeIfAbsent(e.getKey(), k -> new java.util.ArrayList<>())
                        .add(e.getValue()));
        var content = page.getContent().stream()
                .map(entry -> toResponse(entry,
                        byEntry.getOrDefault(entry.getId(), List.of())))
                .toList();
        return new TimelinePage(content,
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

    private TimelineEntryResponse toResponse(TimelineEntry entry,
                                              java.util.List<com.helpdesk.ticket.dto.TimelineDtos.AttachmentView> attachments) {
        return new TimelineEntryResponse(
                entry.getId(), entry.getType(), entry.getEventType(),
                entry.getAuthor() == null ? null : entry.getAuthor().getId(),
                entry.getAuthor() == null ? null : entry.getAuthor().getName(),
                entry.getBody(), entry.getPayload(), entry.getCreatedAt(),
                attachments);
    }
}
