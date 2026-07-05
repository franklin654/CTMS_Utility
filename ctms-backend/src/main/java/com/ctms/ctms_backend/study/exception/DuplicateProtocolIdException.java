package com.ctms.ctms_backend.study.exception;

public class DuplicateProtocolIdException extends RuntimeException {
    public DuplicateProtocolIdException(String protocolId) {
        super("Protocol ID already in use: " + protocolId);
    }
}
