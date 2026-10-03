package com.helpdesk.ticket;

import java.util.Map;
import java.util.Set;

public enum TicketStatus {
    ABERTO, EM_ATENDIMENTO, AGUARDANDO_CLIENTE, RESOLVIDO, FECHADO;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            ABERTO, Set.of(EM_ATENDIMENTO),
            EM_ATENDIMENTO, Set.of(AGUARDANDO_CLIENTE, RESOLVIDO),
            AGUARDANDO_CLIENTE, Set.of(EM_ATENDIMENTO),
            RESOLVIDO, Set.of(FECHADO),
            FECHADO, Set.of());

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
