package com.helpdesk.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineRepository extends JpaRepository<TimelineEntry, Long> {
    Page<TimelineEntry> findByTicketId(Long ticketId, Pageable pageable);
    boolean existsByTicketIdAndEventType(Long ticketId, EventType eventType);
}
