package com.helpdesk.ticket;

import com.helpdesk.security.UserContext;
import com.helpdesk.ticket.dto.TicketDtos.AssignRequest;
import com.helpdesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.TicketPage;
import com.helpdesk.ticket.dto.TicketDtos.TicketResponse;
import com.helpdesk.ticket.dto.TicketDtos.TransitionRequest;
import com.helpdesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import com.helpdesk.ticket.dto.TimelineDtos.CommentResponse;
import com.helpdesk.ticket.dto.TimelineDtos.TimelinePage;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TimelineService timelineService;

    public TicketController(TicketService ticketService, TimelineService timelineService) {
        this.ticketService = ticketService;
        this.timelineService = timelineService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal UserContext current,
                                                 @Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(request, current));
    }

    @GetMapping
    public TicketPage list(@AuthenticationPrincipal UserContext current,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String priority,
                           @RequestParam(required = false) Boolean slaBreached,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1
                ? Sort.Direction.fromString(sortParts[1])
                : Sort.Direction.DESC;
        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(direction, sortParts[0]));
        return ticketService.list(status, priority, slaBreached, q, current, pageable);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@AuthenticationPrincipal UserContext current, @PathVariable Long id) {
        return ticketService.get(id, current);
    }

    @PatchMapping("/{id}")
    public TicketResponse update(@AuthenticationPrincipal UserContext current,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(id, request, current);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserContext current,
                                       @PathVariable Long id) {
        ticketService.delete(id, current);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    public TicketResponse transition(@AuthenticationPrincipal UserContext current,
                                     @PathVariable Long id,
                                     @Valid @RequestBody TransitionRequest request) {
        return ticketService.transition(id, request, current);
    }

    @GetMapping("/{id}/timeline")
    public TimelinePage timeline(@AuthenticationPrincipal UserContext current,
                                 @PathVariable Long id,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        ticketService.loadOwned(id, current);
        return timelineService.timeline(id, PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @PostMapping(value = "/{id}/comments", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommentResponse> comment(@AuthenticationPrincipal UserContext current,
                                                   @PathVariable Long id,
                                                   @RequestParam String body,
                                                   @RequestPart(value = "files", required = false)
                                                   org.springframework.web.multipart.MultipartFile[] files) {
        ticketService.loadOwned(id, current);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timelineService.addComment(id, current, body, files));
    }

    @PreAuthorize("hasRole('AGENTE')")
    @PostMapping("/{id}/assign")
    public TicketResponse assign(@AuthenticationPrincipal UserContext current,
                                 @PathVariable Long id,
                                 @RequestBody(required = false) AssignRequest request) {
        return ticketService.assign(id, request, current);
    }

    @PreAuthorize("hasRole('AGENTE')")
    @DeleteMapping("/{id}/assign")
    public TicketResponse release(@AuthenticationPrincipal UserContext current,
                                  @PathVariable Long id) {
        return ticketService.release(id, current);
    }
}
