package com.helpdesk.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTimelineEntryId(Long timelineEntryId);

    @Query("select a from Attachment a join fetch a.timelineEntry te where te.ticket.id = :ticketId")
    List<Attachment> findByTicketId(@Param("ticketId") Long ticketId);

    @Query("select a from Attachment a join fetch a.timelineEntry te where te.ticket.id = :ticketId and a.id = :id")
    Optional<Attachment> findByIdAndTicketId(@Param("id") Long id, @Param("ticketId") Long ticketId);

    List<Attachment> findByTimelineEntryIdIn(Collection<Long> ids);
}
