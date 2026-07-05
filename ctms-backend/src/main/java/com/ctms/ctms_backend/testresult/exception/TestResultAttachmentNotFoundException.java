package com.ctms.ctms_backend.testresult.exception;

public class TestResultAttachmentNotFoundException extends RuntimeException {

    public TestResultAttachmentNotFoundException(Long id) {
        super("Test result attachment not found: " + id);
    }
}
