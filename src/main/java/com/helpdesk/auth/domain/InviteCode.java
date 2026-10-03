package com.helpdesk.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "invite_codes")
public class InviteCode {

    @Id
    @Column(length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    @Column(name = "used_at")
    private Instant usedAt;

    protected InviteCode() {
    }

    public InviteCode(String code, Role role, Instant expiresAt) {
        this.code = code;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public Role getRole() { return role; }
    public Instant getExpiresAt() { return expiresAt; }
    public User getUsedBy() { return usedBy; }
    public Instant getUsedAt() { return usedAt; }

    public void markUsed(User user, Instant now) {
        this.usedBy = user;
        this.usedAt = now;
    }

    public boolean isAvailable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
