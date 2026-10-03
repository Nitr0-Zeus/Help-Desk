package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Priority priority;

    @Column(name = "first_response_hours", nullable = false)
    private int firstResponseHours;

    @Column(name = "resolution_hours", nullable = false)
    private int resolutionHours;

    @Column(nullable = false)
    private boolean active = true;

    protected SlaPolicy() {
    }

    public SlaPolicy(Priority priority, int firstResponseHours, int resolutionHours) {
        this.priority = priority;
        this.firstResponseHours = firstResponseHours;
        this.resolutionHours = resolutionHours;
    }

    public Long getId() { return id; }
    public Priority getPriority() { return priority; }
    public int getFirstResponseHours() { return firstResponseHours; }
    public int getResolutionHours() { return resolutionHours; }
    public boolean isActive() { return active; }
}
