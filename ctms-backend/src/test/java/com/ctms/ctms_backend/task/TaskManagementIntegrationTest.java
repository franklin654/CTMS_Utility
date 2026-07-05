package com.ctms.ctms_backend.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.notification.NotificationRepository;
import com.ctms.ctms_backend.site.dto.AssignCraRequest;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest;
import com.ctms.ctms_backend.site.service.SiteActivationService;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.service.SubjectService;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.entity.TaskStatus;
import com.ctms.ctms_backend.task.repository.TaskRepository;
import com.ctms.ctms_backend.task.service.TaskEscalationService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.dto.MarkVisitMissedRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.dto.VisitResponse;
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.service.VisitService;
import com.ctms.ctms_backend.visit.service.VisitTemplateService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), mirrors SiteManagementIntegrationTest / VisitManagementIntegrationTest.
 */
@SpringBootTest
@Transactional
class TaskManagementIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SiteActivationService siteActivationService;
    @Autowired private SubjectService subjectService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskEscalationService taskEscalationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User createTestUser(String username, String roleCode) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@ctms.local");
        user.setFullName("Integration Test User");
        user.setPasswordHash(passwordEncoder.encode("Integration!Test2026Pass"));
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        user.setRoles(new HashSet<>(List.of(role)));
        return userRepository.save(user);
    }

    private Task findTaskByEntity(String entityName, Long entityId, String eventCode) {
        return taskRepository.findAll().stream()
                .filter(t -> entityName.equals(t.getEntityName()) && entityId.equals(t.getEntityId()) && eventCode.equals(t.getEventCode()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void subjectEnrolled_autoCreatesTaskWithCorrectOwnerAndEscalationTarget() {
        User manager = createTestUser("task-mgr-it1", Role.STUDY_MANAGER);
        User coordinator = createTestUser("task-coord-it1", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Task IT Trial 1", "TASK-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "TASK-IT-SITE-001", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());

        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        Task task = findTaskByEntity("Subject", subject.id(), "SUBJECT_ENROLLED");
        assertEquals(coordinator.getUsername(), task.getOwner().getUsername());
        assertEquals(manager.getUsername(), task.getEscalationTarget().getUsername());
        assertEquals(TaskStatus.OPEN, task.getStatus());
        assertTrue(task.getDueAt().isAfter(Instant.now()));
    }

    @Test
    void siteActivated_noCra_createsTask_withCra_doesNotCreateTask() {
        User manager = createTestUser("task-mgr-it2", Role.STUDY_MANAGER);
        createTestUser("task-admin-it2", Role.ADMIN);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Task IT Trial 2", "TASK-IT-PROTO-2", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "TASK-IT-SITE-002", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());

        long tasksBefore = taskRepository.count();
        completeAllChecklistItems(site.id(), manager.getUsername());
        long tasksAfter = taskRepository.count();
        assertEquals(1, tasksAfter - tasksBefore, "expected exactly one SITE_ACTIVATED task since no CRA was assigned");

        Task task = findTaskByEntity("Site", site.id(), "SITE_ACTIVATED");
        assertEquals(manager.getUsername(), task.getOwner().getUsername());
        assertEquals("task-admin-it2", task.getEscalationTarget().getUsername());
    }

    @Test
    void siteActivated_withCraAlreadyAssigned_doesNotCreateTask() {
        User manager = createTestUser("task-mgr-it3", Role.STUDY_MANAGER);
        User cra = createTestUser("task-cra-it3", Role.CRA_MONITOR);
        createTestUser("task-admin-it3", Role.ADMIN);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Task IT Trial 3", "TASK-IT-PROTO-3", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "TASK-IT-SITE-003", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        siteService.assignCra(site.id(), new AssignCraRequest(cra.getUsername(), null), manager.getUsername());

        long tasksBefore = taskRepository.count();
        completeAllChecklistItems(site.id(), manager.getUsername());
        long tasksAfter = taskRepository.count();
        assertEquals(0, tasksAfter - tasksBefore, "expected no task since a CRA was already assigned");
    }

    @Test
    void visitMissed_autoCreatesTask() {
        User manager = createTestUser("task-mgr-it4", Role.STUDY_MANAGER);
        User coordinator = createTestUser("task-coord-it4", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Task IT Trial 4", "TASK-IT-PROTO-4", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "TASK-IT-SITE-004", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null), manager.getUsername());

        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        VisitResponse visit = schedule.visits().get(0);
        visitService.markMissed(visit.id(), new MarkVisitMissedRequest("Subject unreachable"), coordinator.getUsername());

        Task task = findTaskByEntity("Visit", visit.id(), "VISIT_MISSED");
        assertEquals(coordinator.getUsername(), task.getOwner().getUsername());
        assertEquals(manager.getUsername(), task.getEscalationTarget().getUsername());

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "Task".equals(a.getEntityName()) && String.valueOf(task.getId()).equals(a.getEntityId()))
                .count();
        assertTrue(auditCount >= 1);
    }

    @Test
    void escalationSweep_reassignsOwnershipAndNotifiesBothParties() {
        User manager = createTestUser("task-mgr-it5", Role.STUDY_MANAGER);
        User coordinator = createTestUser("task-coord-it5", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Task IT Trial 5", "TASK-IT-PROTO-5", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "TASK-IT-SITE-005", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());

        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        Task task = findTaskByEntity("Subject", subject.id(), "SUBJECT_ENROLLED");
        // Force the SLA to have already breached.
        task.setDueAt(Instant.now().minusSeconds(60));
        taskRepository.save(task);

        taskEscalationService.runEscalationSweep();

        Task escalated = taskRepository.findById(task.getId()).orElseThrow();
        assertTrue(escalated.isEscalated());
        assertEquals(manager.getUsername(), escalated.getOwner().getUsername());

        long notificationCount = notificationRepository.findAll().stream()
                .filter(n -> "TASK_ESCALATED".equals(n.getType()))
                .count();
        assertEquals(2, notificationCount, "expected notifications for both the original owner and the escalation target");
    }

    private void completeAllChecklistItems(Long siteId, String actorUsername) {
        for (String itemType : List.of(
                "FEASIBILITY_COMPLETION", "IRB_EC_APPROVAL", "CONTRACT_COMPLETION",
                "ESSENTIAL_DOCUMENTS_SUBMISSION", "SITE_INITIATION_VISIT")) {
            siteActivationService.updateChecklistItem(
                    siteId, itemType, new UpdateChecklistItemRequest("COMPLETE", null, "done"), actorUsername);
        }
    }
}
