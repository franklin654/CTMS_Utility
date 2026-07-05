package com.ctms.ctms_backend.study.exception;

import com.ctms.ctms_backend.study.entity.StudyStatus;

public class StudyFieldLockedException extends RuntimeException {
    public StudyFieldLockedException(String field, StudyStatus status) {
        super("Field '" + field + "' is locked once study is out of DRAFT (current status: " + status + ")");
    }
}
