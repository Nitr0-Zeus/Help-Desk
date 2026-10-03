package com.helpdesk.attachment;

import com.helpdesk.security.UserContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final FileStorage storage;

    public AttachmentController(AttachmentService attachmentService, FileStorage storage) {
        this.attachmentService = attachmentService;
        this.storage = storage;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal UserContext current,
                                                        @PathVariable Long id) {
        Attachment attachment = attachmentService.resolve(id, current);
        String safeName = attachment.getOriginalFilename().replace("\"", "");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .body(new InputStreamResource(storage.open(attachment.getStorageName())));
    }
}
