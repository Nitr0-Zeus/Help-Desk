package com.helpdesk.attachment;

import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.error.PayloadTooLargeException;
import com.helpdesk.common.error.UnsupportedMediaTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Component
public class FileStorage {

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/gif",
            "application/pdf", "text/plain", "application/zip");

    private final Path root;

    public FileStorage(@Value("${helpdesk.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload dir " + uploadDir, e);
        }
    }

    public void validate(MultipartFile file) {
        if (file.getSize() > MAX_SIZE) {
            throw new PayloadTooLargeException(
                    "File exceeds the 10 MB limit: " + file.getOriginalFilename());
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!ALLOWED.contains(contentType)) {
            throw new UnsupportedMediaTypeException("Content type not allowed: " + contentType);
        }
    }

    public String store(MultipartFile file) {
        String storageName = UUID.randomUUID().toString();
        try {
            Files.copy(file.getInputStream(), root.resolve(storageName),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store file", e);
        }
        return storageName;
    }

    public InputStream open(String storageName) {
        Path path = root.resolve(storageName);
        if (!Files.exists(path)) {
            throw new NotFoundException("Attachment file not found");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open file", e);
        }
    }

    public void delete(String storageName) {
        try {
            Files.deleteIfExists(root.resolve(storageName));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete file", e);
        }
    }
}
