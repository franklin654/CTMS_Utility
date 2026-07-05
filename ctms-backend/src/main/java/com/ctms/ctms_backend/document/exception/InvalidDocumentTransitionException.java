package com.ctms.ctms_backend.document.exception;

import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;

public class InvalidDocumentTransitionException extends RuntimeException {
    public InvalidDocumentTransitionException(DocumentVersionStatus from, DocumentVersionStatus to) {
        super("Cannot transition document version from " + from + " to " + to);
    }

    public InvalidDocumentTransitionException(String message) {
        super(message);
    }
}
