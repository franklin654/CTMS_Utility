package com.ctms.ctms_backend.task.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.entity.TaskStatus;
import com.ctms.ctms_backend.task.repository.TaskRepository;
import com.ctms.ctms_backend.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BRD Epic 5 Story 03 (Automatic Task Escalations). Runs hourly (not daily like Phase 5's visit
 * alert sweep -- Task SLAs are measured in hours, so a once-a-day check would let same-day
 * breaches sit unescalated for up to 24h). Escalation reassigns ownership to the resolved
 * escalation target (confirmed design: "may be reassigned" defaults to always-reassign) while
 * notifying the original owner, and is single-level -- escalated is a one-way flag, never reset. */
@Service
public class TaskEscalationService {

    private static final List<TaskStatus> ESCALATABLE_STATUSES = List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS);

    private final TaskRepository taskRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public TaskEscalationService(
            TaskRepository taskRepository, AuditService auditService, NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void runEscalationSweep() {
        List<Task> breached = taskRepository.findByStatusInAndEscalatedFalseAndDueAtBefore(
                ESCALATABLE_STATUSES, Instant.now());

        for (Task task : breached) {
            User originalOwner = task.getOwner();
            User escalationTarget = task.getEscalationTarget();

            task.setEscalated(true);
            task.setEscalatedAt(Instant.now());
            task.setOwner(escalationTarget);
            taskRepository.save(task);

            auditService.record(
                    "Task", String.valueOf(task.getId()), AuditAction.STATE_CHANGE,
                    "owner=" + originalOwner.getUsername(), "escalated to " + escalationTarget.getUsername(), null);

            notificationService.notify(
                    originalOwner.getId(), "TASK_ESCALATED", "Task escalated: " + task.getTitle(),
                    "This task's SLA has breached and it was reassigned to " + escalationTarget.getUsername() + ".",
                    "/tasks/" + task.getId());
            notificationService.notify(
                    escalationTarget.getId(), "TASK_ESCALATED", "Task escalated to you: " + task.getTitle(),
                    "This task's SLA breached under " + originalOwner.getUsername() + " and has been reassigned to you.",
                    "/tasks/" + task.getId());
        }
    }
}
