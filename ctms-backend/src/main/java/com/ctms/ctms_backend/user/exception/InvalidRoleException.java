package com.ctms.ctms_backend.user.exception;

public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String roleCode) {
        super("'" + roleCode + "' is not a recognized role code");
    }
}
