package com.ctms.ctms_backend.audit;

/** Canonical action codes recorded in {@link AuditLog#getAction()}. Modules may use additional
 * domain-specific codes (e.g. "APPROVE", "REJECT") as free-form strings; these cover the common cases. */
public final class AuditAction {
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String STATE_CHANGE = "STATE_CHANGE";
    public static final String ACCESS = "ACCESS";
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";

    private AuditAction() {}
}
