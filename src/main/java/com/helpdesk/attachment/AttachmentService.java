package com.helpdesk.attachment;

import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.security.UserContext;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TimelineEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final FileStorage storage;

    public AttachmentService(AttachmentRepository attachments, TicketRepository tickets,
                             UserRepository users, FileStorage storage) {
        this.attachments = attachments;
        this.tickets = tickets;
        this.users = users;
        this.storage = storage;
    }

    @Transactional
    public List<Attachment> attach(TimelineEntry entry, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        return Arrays.stream(files)
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> {
                    storage.validate(file);
                    String storageName = storage.store(file);
                    return attachments.save(new Attachment(entry,
                            file.getOriginalFilename() == null ? "file" : file.getOriginalFilename(),
                            storageName, file.getContentType(), file.getSize()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Attachment resolve(Long id, UserContext actor) {
        Attachment attachment = attachments.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        Ticket ticket = attachment.getTimelineEntry().getTicket();
        boolean allowed = actor.isAgent()
                || ticket.getCreatedBy().getId().equals(actor.id());
        if (!allowed) {
            throw new NotFoundException("Attachment not found");
        }
        return attachment;
    }

    /**
     * Deletes the on-disk files of every attachment of the given ticket.
     * Row cleanup is handled by the DB cascade when the ticket is deleted.
     */
    @Transactional
    public void deleteFilesOfTicket(Ticket ticket) {
        attachments.findByTicketId(ticket.getId())
                .forEach(a -> storage.delete(a.getStorageName()));
    }

    public User loadAuthor(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
