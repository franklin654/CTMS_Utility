package com.ctms.ctms_backend.task.rules;

/** Drools fact -- what TASK_RULES_DEFAULT decides for a given trigger event: SLA hours, the
 * descriptive owner/escalation role labels (actual User resolution happens at the event-listener
 * call site, not here -- see TaskService.createTask), and priority. */
public class TaskRuleOutcome {

    private final int slaHours;
    private final String ownerRole;
    private final String escalationRole;
    private final String priority;

    public TaskRuleOutcome(int slaHours, String ownerRole, String escalationRole, String priority) {
        this.slaHours = slaHours;
        this.ownerRole = ownerRole;
        this.escalationRole = escalationRole;
        this.priority = priority;
    }

    public int getSlaHours() {
        return slaHours;
    }

    public String getOwnerRole() {
        return ownerRole;
    }

    public String getEscalationRole() {
        return escalationRole;
    }

    public String getPriority() {
        return priority;
    }
}
