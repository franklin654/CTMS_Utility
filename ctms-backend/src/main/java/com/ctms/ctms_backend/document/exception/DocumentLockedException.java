package com.ctms.ctms_backend.document.exception;

public class DocumentLockedException extends RuntimeException {
    public DocumentLockedException(Long documentId, int versionNumber) {
        super("Document " + documentId + " version " + versionNumber + " is locked from further edits");
    }
}
