package com.helpdesk.ticket;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.common.error.ConflictException;
import com.helpdesk.common.error.ForbiddenActionException;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.security.UserContext;
import com.helpdesk.sla.SlaCalculator;
import com.helpdesk.ticket.dto.TicketDtos.AssignRequest;
import com.helpdesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.TicketPage;
import com.helpdesk.ticket.dto.TicketDtos.TicketResponse;
import com.helpdesk.ticket.dto.TicketDtos.TransitionRequest;
import com.helpdesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import com.helpdesk.user.UserService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final UserService userService;
    private final SlaCalculator slaCalculator;
    private final TimeProvider time;

    public TicketService(TicketRepository tickets, UserService userService,
                         SlaCalculator slaCalculator, TimeProvider time) {
        this.tickets = tickets;
        this.userService = userService;
        this.slaCalculator = slaCalculator;
        this.time = time;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, UserContext actor) {
        User creator = userService.loadRequired(actor.id());
        var now = time.now();
        TicketStatus initial = actor.isAgent() ? TicketStatus.EM_ATENDIMENTO : TicketStatus.ABERTO;
        Ticket ticket = new Ticket(request.title().trim(), request.description().trim(),
                request.priority(), initial, creator, now);
        if (actor.isAgent()) {
            ticket.setAssignedTo(creator);
        }
        // protocol is NOT NULL+UNIQUE from the first insert, but the padded
        // final value ("TK-%05d") needs the generated id; use a short unique
        // placeholder and rewrite it right after the insert
        ticket.setProtocol("TK-" + UUID.randomUUID().toString().substring(0, 8));
        tickets.saveAndFlush(ticket);
        var deadlines = slaCalculator.calculate(request.priority(), now);
        ticket.setFirstResponseDueAt(deadlines.firstResponseDueAt());
        ticket.setDueAt(deadlines.dueAt());
        tickets.saveAndFlush(ticket);
        ticket.setProtocol(String.format("TK-%05d", ticket.getId()));
        return toResponse(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Long id, UserContext actor) {
        return toResponse(loadOwned(id, actor));
    }

    @Transactional(readOnly = true)
    public TicketPage list(String status, String priority, Boolean breached, String q,
                           UserContext actor, Pageable pageable) {
        Page<Ticket> page = tickets.findAll(buildSpec(actor, status, priority, breached, q), pageable);
        return new TicketPage(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request, UserContext actor) {
        Ticket ticket = loadOwned(id, actor);
        if (!actor.isAgent() && ticket.getStatus() != TicketStatus.ABERTO) {
            throw new ConflictException("Tickets can only be edited while ABERTO");
        }
        if (ticket.getStatus() == TicketStatus.FECHADO) {
            throw new ConflictException("Closed tickets cannot be edited");
        }
        if (request.title() != null && !request.title().isBlank()) {
            ticket.setTitle(request.title().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            ticket.setDescription(request.description().trim());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        ticket.touch(time.now());
        return toResponse(tickets.save(ticket));
    }

    @Transactional
    public void delete(Long id, UserContext actor) {
        Ticket ticket = loadOwned(id, actor);
        if (!ticket.getCreatedBy().getId().equals(actor.id())) {
            throw new ForbiddenActionException("Only the creator can delete this ticket");
        }
        if (ticket.getStatus() != TicketStatus.ABERTO) {
            throw new ConflictException("Tickets can only be deleted while ABERTO");
        }
        tickets.delete(ticket);
    }

    @Transactional
    public TicketResponse transition(Long id, TransitionRequest request, UserContext actor) {
        Ticket ticket = loadOwned(id, actor);
        TicketStatus target = request.status();
        if (!ticket.getStatus().canTransitionTo(target)) {
            throw new ConflictException("Cannot move ticket from " + ticket.getStatus() + " to " + target);
        }
        if (!actor.isAgent() && target != TicketStatus.FECHADO) {
            throw new ForbiddenActionException("Clients can only close resolved tickets");
        }
        if (target == TicketStatus.FECHADO
                && !actor.isAgent()
                && !ticket.getCreatedBy().getId().equals(actor.id())) {
            throw new ForbiddenActionException("Only the ticket creator can close it");
        }
        var now = time.now();
        TicketStatus from = ticket.getStatus();
        switch (target) {
            case EM_ATENDIMENTO -> {
                if (from == TicketStatus.ABERTO && actor.isAgent()) {
                    ticket.setAssignedTo(userService.loadRequired(actor.id()));
                }
            }
            case AGUARDANDO_CLIENTE -> ticket.setWaitingSince(now);
            case RESOLVIDO -> ticket.setResolvedAt(now);
            case FECHADO -> ticket.setClosedAt(now);
            default -> { }
        }
        ticket.setStatus(target);
        ticket.touch(now);
        return toResponse(tickets.save(ticket));
    }

    @Transactional
    public TicketResponse assign(Long id, AssignRequest request, UserContext actor) {
        requireAgent(actor);
        Ticket ticket = loadOwned(id, actor);
        if (ticket.getStatus() != TicketStatus.ABERTO
                && ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new ConflictException("Tickets can only be assigned while ABERTO or EM_ATENDIMENTO");
        }
        Long agentId = request == null || request.agentId() == null ? actor.id() : request.agentId();
        User agent = userService.loadRequired(agentId);
        if (agent.getRole() != Role.AGENTE) {
            throw new ConflictException("Target user is not an agent");
        }
        ticket.setAssignedTo(agent);
        ticket.touch(time.now());
        return toResponse(tickets.save(ticket));
    }

    @Transactional
    public TicketResponse release(Long id, UserContext actor) {
        requireAgent(actor);
        Ticket ticket = loadOwned(id, actor);
        if (ticket.getStatus() != TicketStatus.ABERTO
                && ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new ConflictException("Tickets can only be unassigned while ABERTO or EM_ATENDIMENTO");
        }
        ticket.setAssignedTo(null);
        ticket.touch(time.now());
        return toResponse(tickets.save(ticket));
    }

    public Ticket loadOwned(Long id, UserContext actor) {
        Ticket ticket = tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (!actor.isAgent() && !ticket.getCreatedBy().getId().equals(actor.id())) {
            throw new NotFoundException("Ticket not found");
        }
        return ticket;
    }

    private void requireAgent(UserContext actor) {
        if (!actor.isAgent()) {
            throw new ForbiddenActionException("Only agents can assign tickets");
        }
    }

    private Specification<Ticket> buildSpec(UserContext actor, String status, String priority,
                                            Boolean breached, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.isAgent()) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), actor.id()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"),
                        TicketStatus.valueOf(status.toUpperCase(Locale.ROOT))));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"),
                        Priority.valueOf(priority.toUpperCase(Locale.ROOT))));
            }
            if (breached != null) {
                predicates.add(cb.equal(root.get("slaBreached"), breached));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("protocol")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getProtocol(), t.getTitle(), t.getDescription(),
                t.getPriority(), t.getStatus(),
                t.getCreatedBy().getId(), t.getCreatedBy().getName(),
                t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
                t.getAssignedTo() == null ? null : t.getAssignedTo().getName(),
                t.getCreatedAt(), t.getUpdatedAt(),
                t.getFirstResponseAt(), t.getWaitingSince(),
                t.getResolvedAt(), t.getClosedAt(),
                t.getFirstResponseDueAt(), t.getDueAt(),
                t.isSlaBreached());
    }
}
