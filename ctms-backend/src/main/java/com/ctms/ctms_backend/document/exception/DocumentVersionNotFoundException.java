package com.ctms.ctms_backend.document.exception;

public class DocumentVersionNotFoundException extends RuntimeException {
    public DocumentVersionNotFoundException(Long documentId, int versionNumber) {
        super("Document " + documentId + " has no version " + versionNumber);
    }
}
