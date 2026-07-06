package com.ctms.ctms_backend.subject.exception;

public class NoPortalAccountException extends RuntimeException {
    public NoPortalAccountException(Long subjectId) {
        super("Subject " + subjectId + " has no portal account to reset");
    }
}
