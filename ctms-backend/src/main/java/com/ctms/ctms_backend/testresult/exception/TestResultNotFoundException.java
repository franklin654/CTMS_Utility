package com.ctms.ctms_backend.testresult.exception;

public class TestResultNotFoundException extends RuntimeException {

    public TestResultNotFoundException(Long id) {
        super("Test result not found: " + id);
    }
}
