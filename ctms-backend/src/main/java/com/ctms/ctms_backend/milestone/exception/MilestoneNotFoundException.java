package com.ctms.ctms_backend.milestone.exception;

public class MilestoneNotFoundException extends RuntimeException {

    public MilestoneNotFoundException(Long id) {
        super("Milestone not found: " + id);
    }
}
