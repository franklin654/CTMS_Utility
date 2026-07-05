package com.ctms.ctms_backend.task.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.task.dto.TaskResponse;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.entity.TaskPriority;
import com.ctms.ctms_backend.task.entity.TaskStatus;
import com.ctms.ctms_backend.task.exception.InvalidTaskTransitionException;
import com.ctms.ctms_backend.task.exception.TaskNotFoundException;
import com.ctms.ctms_backend.task.repository.TaskRepository;
import com.ctms.ctms_backend.task.rules.TaskRuleOutcome;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BRD Epic 5 Story 01 (Auto-Create Tasks) & Story 02 (SLA-Based Tracking). Tasks are only ever
 * system-created (no manual-create story exists in the BRD) -- event listeners in
 * SubjectService/SiteActivationService/VisitService resolve the actual owner/escalation-target
 * User using domain relationships already in scope, then call createTask here with those resolved
 * IDs plus the trigger event code; TaskRuleService supplies the SLA/priority/role-label data. */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskRuleService taskRuleService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public TaskService(
            TaskRepository taskRepository,
            UserRepository userRepository,
            TaskRuleService taskRuleService,
            AuditService auditService,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.taskRuleService = taskRuleService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TaskResponse createTask(
            String eventCode,
            String title,
            String description,
            String entityName,
            Long entityId,
            Long ownerId,
            Long escalationTargetId,
            String actorUsername) {
        TaskRuleOutcome outcome = taskRuleService.evaluate(eventCode);
        User owner = findUser(ownerId);
        User escalationTarget = findUser(escalationTargetId);
        User actor = currentUser(actorUsername);

        Task task = new Task();
        task.setEventCode(eventCode);
        task.setTitle(title);
        task.setDescription(description);
        task.setEntityName(entityName);
        task.setEntityId(entityId);
        task.setOwner(owner);
        task.setOwnerRole(outcome.getOwnerRole());
        task.setEscalationTarget(escalationTarget);
        task.setEscalationRole(outcome.getEscalationRole());
        task.setPriority(TaskPriority.valueOf(outcome.getPriority()));
        task.setStatus(TaskStatus.OPEN);
        task.setDueAt(Instant.now().plus(outcome.getSlaHours(), ChronoUnit.HOURS));
        task.setCreatedBy(actor);
        task.setModifiedBy(actor);
        task = taskRepository.save(task);

        auditService.record(
                "Task", String.valueOf(task.getId()), AuditAction.CREATE,
                null, title + " (event " + eventCode + ", owner " + owner.getUsername() + ")", null);
        notificationService.notify(
                owner.getId(), "TASK_ASSIGNED", "New task: " + title,
                description, "/tasks/" + task.getId());

        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> myTasks(Long ownerId, Pageable pageable) {
        return taskRepository.findByOwnerIdOrderByDueAtAsc(ownerId, pageable).map(TaskResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> allTasks(Pageable pageable) {
        return taskRepository.findAll(pageable).map(TaskResponse::from);
    }

    @Transactional
    public TaskResponse start(Long id, String actorUsername) {
        Task task = findTask(id);
        User actor = currentUser(actorUsername);
        guardOwnerOrAdmin(task, actor);

        if (task.getStatus() != TaskStatus.OPEN) {
            throw new InvalidTaskTransitionException("Cannot start task from status " + task.getStatus());
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setModifiedBy(actor);
        task = taskRepository.save(task);

        auditService.record("Task", String.valueOf(id), AuditAction.STATE_CHANGE, TaskStatus.OPEN.name(), TaskStatus.IN_PROGRESS.name(), null);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse complete(Long id, String actorUsername) {
        Task task = findTask(id);
        User actor = currentUser(actorUsername);
        guardOwnerOrAdmin(task, actor);

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new InvalidTaskTransitionException("Task is already completed");
        }
        TaskStatus previous = task.getStatus();
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setModifiedBy(actor);
        task = taskRepository.save(task);

        auditService.record("Task", String.valueOf(id), AuditAction.STATE_CHANGE, previous.name(), TaskStatus.COMPLETED.name(), null);
        return TaskResponse.from(task);
    }

    private void guardOwnerOrAdmin(Task task, User actor) {
        if (!task.getOwner().getId().equals(actor.getId()) && !actor.hasRole(Role.ADMIN)) {
            throw new InvalidTaskTransitionException("Only the task owner or an admin can update this task");
        }
    }

    Task findTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("No user " + id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
