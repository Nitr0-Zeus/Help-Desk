package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.Priority;
import com.helpdesk.ticket.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Priority priority) {
    }

    public record UpdateTicketRequest(
            @Size(max = 160) String title,
            @Size(max = 5000) String description,
            Priority priority) {
    }

    public record TransitionRequest(
            @NotNull TicketStatus status,
            @Size(max = 5000) String comment) {
    }

    public record AssignRequest(Long agentId) {
    }

    public record TicketResponse(
            Long id, String protocol, String title, String description,
            Priority priority, TicketStatus status,
            Long createdById, String createdByName,
            Long assignedToId, String assignedToName,
            Instant createdAt, Instant updatedAt,
            Instant firstResponseAt, Instant waitingSince,
            Instant resolvedAt, Instant closedAt,
            Instant firstResponseDueAt, Instant dueAt,
            boolean slaBreached) {
    }

    public record TicketPage(List<TicketResponse> content, int page, int size,
                             long totalElements, int totalPages) {
    }
}
