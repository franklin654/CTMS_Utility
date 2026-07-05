package com.ctms.ctms_backend.security.exception;

import java.util.List;

public class PasswordPolicyViolationException extends RuntimeException {

    private final List<String> violations;

    public PasswordPolicyViolationException(List<String> violations) {
        super("Password does not meet policy requirements: " + String.join("; ", violations));
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
