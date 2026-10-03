package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.EventType;
import com.helpdesk.ticket.TimelineType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TimelineDtos {

    private TimelineDtos() {
    }

    public record CommentRequest(@NotBlank @Size(max = 5000) String body) {
    }

    public record TimelineEntryResponse(Long id, TimelineType type, EventType eventType,
                                        Long authorId, String authorName, String body,
                                        String payload, Instant createdAt) {
    }

    public record TimelinePage(List<TimelineEntryResponse> content, int page, int size,
                               long totalElements, int totalPages) {
    }
}
