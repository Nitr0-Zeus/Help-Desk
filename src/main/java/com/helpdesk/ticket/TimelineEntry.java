package com.helpdesk.ticket;

import com.helpdesk.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "timeline_entries")
public class TimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimelineType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private EventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TimelineEntry() {
    }

    public static TimelineEntry comment(Ticket ticket, User author, String body, Instant now) {
        TimelineEntry entry = base(ticket, TimelineType.COMMENT, author, now);
        entry.body = body;
        return entry;
    }

    public static TimelineEntry event(Ticket ticket, EventType eventType, User author,
                                      String payloadJson, Instant now) {
        TimelineEntry entry = base(ticket, TimelineType.EVENT, author, now);
        entry.eventType = eventType;
        entry.payload = payloadJson;
        return entry;
    }

    private static TimelineEntry base(Ticket ticket, TimelineType type, User author, Instant now) {
        TimelineEntry entry = new TimelineEntry();
        entry.ticket = ticket;
        entry.type = type;
        entry.author = author;
        entry.createdAt = now;
        return entry;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public TimelineType getType() { return type; }
    public EventType getEventType() { return eventType; }
    public User getAuthor() { return author; }
    public String getBody() { return body; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
