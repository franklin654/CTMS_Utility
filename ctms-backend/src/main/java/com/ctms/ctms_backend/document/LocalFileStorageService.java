package com.ctms.ctms_backend.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalFileStorageService implements StorageService {

    private final Path basePath;

    public LocalFileStorageService(@Value("${storage.local.base-path}") String basePathProperty) {
        this.basePath = Path.of(basePathProperty).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage directory " + basePath, e);
        }
    }

    @Override
    public String store(InputStream content, String suggestedFileName) {
        String extension = "";
        int dot = suggestedFileName.lastIndexOf('.');
        if (dot >= 0) {
            extension = suggestedFileName.substring(dot);
        }
        String storageKey = UUID.randomUUID() + extension;
        Path target = basePath.resolve(storageKey);
        try {
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file " + suggestedFileName, e);
        }
        return storageKey;
    }

    @Override
    public InputStream retrieve(String storageKey) {
        try {
            return Files.newInputStream(resolveSafely(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafely(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file " + storageKey, e);
        }
    }

    /** Guards against path traversal via a crafted storage key -- resolved paths must stay under basePath. */
    private Path resolveSafely(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
