package com.helpdesk.sla.dto;

import com.helpdesk.ticket.Priority;

public final class SlaDtos {

    private SlaDtos() {
    }

    public record SlaPolicyView(Priority priority, int firstResponseHours,
                                int resolutionHours, boolean active) {
    }
}
