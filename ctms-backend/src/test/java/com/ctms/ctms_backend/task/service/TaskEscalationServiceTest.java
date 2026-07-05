package com.ctms.ctms_backend.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.entity.TaskPriority;
import com.ctms.ctms_backend.task.entity.TaskStatus;
import com.ctms.ctms_backend.task.repository.TaskRepository;
import com.ctms.ctms_backend.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskEscalationServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private TaskEscalationService taskEscalationService;

    private User owner;
    private User escalationTarget;
    private Task task;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("coordinator1");

        escalationTarget = new User();
        escalationTarget.setId(2L);
        escalationTarget.setUsername("studymgr1");

        task = new Task();
        task.setId(100L);
        task.setTitle("Follow up with subject");
        task.setOwner(owner);
        task.setEscalationTarget(escalationTarget);
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(TaskPriority.HIGH);
        task.setEscalated(false);
        task.setDueAt(Instant.now().minusSeconds(3600));

        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void breachedTask_escalatesAndReassignsOwnership() {
        when(taskRepository.findByStatusInAndEscalatedFalseAndDueAtBefore(anyList(), any()))
                .thenReturn(List.of(task));

        taskEscalationService.runEscalationSweep();

        assertTrue(task.isEscalated());
        assertEquals(escalationTarget, task.getOwner());
        verify(notificationService).notify(eq(1L), eq("TASK_ESCALATED"), any(), any(), any());
        verify(notificationService).notify(eq(2L), eq("TASK_ESCALATED"), any(), any(), any());
    }

    @Test
    void noBreachedTasks_noEscalation() {
        when(taskRepository.findByStatusInAndEscalatedFalseAndDueAtBefore(anyList(), any()))
                .thenReturn(List.of());

        taskEscalationService.runEscalationSweep();

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
