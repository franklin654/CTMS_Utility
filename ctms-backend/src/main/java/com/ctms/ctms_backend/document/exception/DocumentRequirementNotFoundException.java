package com.ctms.ctms_backend.document.exception;

public class DocumentRequirementNotFoundException extends RuntimeException {

    public DocumentRequirementNotFoundException(Long id) {
        super("Document requirement not found: " + id);
    }
}
