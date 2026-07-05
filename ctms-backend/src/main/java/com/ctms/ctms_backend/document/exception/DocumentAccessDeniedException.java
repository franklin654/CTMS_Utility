package com.ctms.ctms_backend.document.exception;

/** Distinct from Spring Security's generic AccessDeniedException so the audit-then-403 flow in
 * DocumentAccessControlService gets its own clear message, per Story 05's category-based rules. */
public class DocumentAccessDeniedException extends RuntimeException {
    public DocumentAccessDeniedException(Long documentId) {
        super("Not authorized to access document " + documentId);
    }
}
