package com.ctms.ctms_backend.adverseevent.exception;

public class AdverseEventNotFoundException extends RuntimeException {

    public AdverseEventNotFoundException(Long id) {
        super("Adverse event not found: " + id);
    }
}
