package com.ctms.ctms_backend.rules;

/** Thrown when a proposed {@link RuleDefinition}'s DRL fails to compile -- surfaced as validation
 * feedback to whoever is editing the rule (Phase 10's no-code UI), not a generic server error. */
public class RuleCompilationException extends RuntimeException {
    public RuleCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
