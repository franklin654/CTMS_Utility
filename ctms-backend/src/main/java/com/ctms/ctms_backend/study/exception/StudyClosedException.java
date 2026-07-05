package com.ctms.ctms_backend.study.exception;

/** Study is CLOSEOUT (signed) and fully locked -- per CLAUDE.md 2.5, signed records lock from further edits. */
public class StudyClosedException extends RuntimeException {
    public StudyClosedException(Long id) {
        super("Study " + id + " is closed out and locked from further edits");
    }
}
