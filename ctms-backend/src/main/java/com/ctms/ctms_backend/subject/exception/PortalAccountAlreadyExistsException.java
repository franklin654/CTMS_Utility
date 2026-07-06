package com.ctms.ctms_backend.subject.exception;

public class PortalAccountAlreadyExistsException extends RuntimeException {
    public PortalAccountAlreadyExistsException(Long subjectId) {
        super("Subject " + subjectId + " already has a portal account");
    }
}
