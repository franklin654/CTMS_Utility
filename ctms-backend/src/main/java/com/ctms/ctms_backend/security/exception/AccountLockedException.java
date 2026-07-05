package com.ctms.ctms_backend.security.exception;

import java.time.Instant;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(Instant lockedUntil) {
        super("Account is locked until " + lockedUntil);
    }
}
