package com.ctms.ctms_backend.user.exception;

/** Guards against locking every admin out of the system: refuses to disable or de-role the
 * last remaining enabled ADMIN user. */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("Cannot remove ADMIN role or disable the last remaining active admin user");
    }
}
