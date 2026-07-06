package com.ctms.ctms_backend.document.exception;

public class MissingConsentException extends RuntimeException {

    public MissingConsentException(Long subjectId) {
        super("Subject " + subjectId + " has no CURRENT informed consent document on file");
    }
}
