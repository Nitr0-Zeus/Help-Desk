package com.helpdesk.attachment;

import com.helpdesk.ticket.TimelineEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timeline_entry_id")
    private TimelineEntry timelineEntry;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_name", nullable = false, unique = true, length = 36)
    private String storageName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(TimelineEntry timelineEntry, String originalFilename, String storageName,
                      String contentType, long size) {
        this.timelineEntry = timelineEntry;
        this.originalFilename = originalFilename;
        this.storageName = storageName;
        this.contentType = contentType;
        this.size = size;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public TimelineEntry getTimelineEntry() { return timelineEntry; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageName() { return storageName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public Instant getCreatedAt() { return createdAt; }
}
