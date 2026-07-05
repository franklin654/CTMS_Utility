package com.ctms.ctms_backend.task.rules;

/** Drools fact -- the trigger event that a Task is being auto-created for. */
public class TaskTriggerFact {

    private final String eventCode;

    public TaskTriggerFact(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventCode() {
        return eventCode;
    }
}
