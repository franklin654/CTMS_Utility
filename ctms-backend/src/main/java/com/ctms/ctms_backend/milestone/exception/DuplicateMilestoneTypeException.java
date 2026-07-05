package com.ctms.ctms_backend.milestone.exception;

public class DuplicateMilestoneTypeException extends RuntimeException {

    public DuplicateMilestoneTypeException(Long studyId, String milestoneType) {
        super("Study " + studyId + " already has a " + milestoneType + " milestone");
    }
}
