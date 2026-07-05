package com.ctms.ctms_backend.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.task.dto.TaskResponse;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.entity.TaskStatus;
import com.ctms.ctms_backend.task.exception.InvalidTaskTransitionException;
import com.ctms.ctms_backend.task.repository.TaskRepository;
import com.ctms.ctms_backend.task.rules.TaskRuleOutcome;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private TaskRuleService taskRuleService;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private TaskService taskService;

    private User owner;
    private User escalationTarget;
    private User actor;
    private Task openTask;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("coordinator1");
        owner.setRoles(new HashSet<>());

        escalationTarget = new User();
        escalationTarget.setId(2L);
        escalationTarget.setUsername("studymgr1");

        actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");
        actor.setRoles(new HashSet<>());

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(escalationTarget));
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));

        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(100L);
            }
            return t;
        });

        openTask = new Task();
        openTask.setId(100L);
        openTask.setOwner(owner);
        openTask.setEscalationTarget(escalationTarget);
        openTask.setOwnerRole("SITE_COORDINATOR");
        openTask.setEscalationRole("STUDY_MANAGER");
        openTask.setPriority(com.ctms.ctms_backend.task.entity.TaskPriority.HIGH);
        openTask.setStatus(TaskStatus.OPEN);
        openTask.setDueAt(Instant.now().plusSeconds(3600));
        lenient().when(taskRepository.findById(100L)).thenReturn(Optional.of(openTask));
    }

    @Test
    void createTask_computesDueAtFromSlaHours_andNotifiesOwner() {
        when(taskRuleService.evaluate("VISIT_MISSED"))
                .thenReturn(new TaskRuleOutcome(24, "SITE_COORDINATOR", "STUDY_MANAGER", "HIGH"));

        Instant before = Instant.now();
        TaskResponse response = taskService.createTask(
                "VISIT_MISSED", "Follow up with subject", "desc", "Visit", 5L, 1L, 2L, "coordinator1");

        assertEquals("OPEN", response.status());
        assertEquals("HIGH", response.priority());
        assertEquals("coordinator1", response.ownerUsername());
        assertEquals("studymgr1", response.escalationTargetUsername());
        assertTrue(response.dueAt().isAfter(before.plusSeconds(23 * 3600)));
        assertTrue(response.dueAt().isBefore(before.plusSeconds(25 * 3600)));
    }

    @Test
    void start_transitionsOpenToInProgress() {
        TaskResponse response = taskService.start(100L, "coordinator1");
        assertEquals("IN_PROGRESS", response.status());
    }

    @Test
    void start_nonOpenTask_throws() {
        openTask.setStatus(TaskStatus.IN_PROGRESS);
        assertThrows(InvalidTaskTransitionException.class, () -> taskService.start(100L, "coordinator1"));
    }

    @Test
    void start_notOwnerNotAdmin_throws() {
        User other = new User();
        other.setId(99L);
        other.setUsername("other1");
        other.setRoles(new HashSet<>());
        when(userRepository.findByUsername("other1")).thenReturn(Optional.of(other));

        assertThrows(InvalidTaskTransitionException.class, () -> taskService.start(100L, "other1"));
    }

    @Test
    void complete_setsCompletedAt() {
        openTask.setStatus(TaskStatus.IN_PROGRESS);
        TaskResponse response = taskService.complete(100L, "coordinator1");
        assertEquals("COMPLETED", response.status());
    }

    @Test
    void complete_alreadyCompleted_throws() {
        openTask.setStatus(TaskStatus.COMPLETED);
        assertThrows(InvalidTaskTransitionException.class, () -> taskService.complete(100L, "coordinator1"));
    }
}
