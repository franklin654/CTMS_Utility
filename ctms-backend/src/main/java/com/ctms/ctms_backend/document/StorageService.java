package com.ctms.ctms_backend.document;

import java.io.InputStream;

/**
 * Abstraction over where document bytes actually live. {@link LocalFileStorageService} is the
 * only implementation for now; an S3-backed implementation can be swapped in later purely via
 * Spring configuration, without touching {@link DocumentService}.
 */
public interface StorageService {

    /** Stores the given content and returns an opaque storage key/path to retrieve it later. */
    String store(InputStream content, String suggestedFileName);

    InputStream retrieve(String storageKey);

    void delete(String storageKey);
}
