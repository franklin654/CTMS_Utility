package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.adverseevent.dto.ReportAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.ResolveAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.TransitionAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.exception.InvalidAdverseEventTransitionException;
import com.ctms.ctms_backend.adverseevent.service.AdverseEventService;
import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.service.SubjectService;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.repository.TaskRepository;
import com.ctms.ctms_backend.testresult.dto.CreateTestResultRequest;
import com.ctms.ctms_backend.testresult.dto.TestResultAttachmentResponse;
import com.ctms.ctms_backend.testresult.dto.TestResultResponse;
import com.ctms.ctms_backend.testresult.entity.TestResult;
import com.ctms.ctms_backend.testresult.service.TestResultAttachmentService;
import com.ctms.ctms_backend.testresult.service.TestResultService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.dto.VisitResponse;
import com.ctms.ctms_backend.visit.service.VisitService;
import com.ctms.ctms_backend.visit.service.VisitTemplateService;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), mirrors TaskManagementIntegrationTest / VisitManagementIntegrationTest.
 */
@SpringBootTest
@Transactional
class ClinicalSafetyIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SubjectService subjectService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private TestResultService testResultService;
    @Autowired private TestResultAttachmentService attachmentService;
    @Autowired private AdverseEventService adverseEventService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogRepository;

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

    private SubjectResponse setUpSubjectWithVisit(String suffix, String coordinatorUsername, String managerUsername) {
        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Safety IT Trial " + suffix, "SAFETY-IT-PROTO-" + suffix, "1.0", "PHASE_III", "Acme", null, null, null),
                managerUsername);
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "SAFETY-IT-SITE-" + suffix, "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                managerUsername);
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null), managerUsername);
        return subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinatorUsername);
    }

    @Test
    void recordReviewAndAttachTestResult_fullRoundTrip() {
        User manager = createTestUser("safety-mgr-it1", Role.STUDY_MANAGER);
        User coordinator = createTestUser("safety-coord-it1", Role.SITE_COORDINATOR);
        User investigator = createTestUser("safety-inv-it1", Role.INVESTIGATOR);

        SubjectResponse subject = setUpSubjectWithVisit("1", coordinator.getUsername(), manager.getUsername());
        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        VisitResponse visit = schedule.visits().get(0);

        TestResultResponse recorded = testResultService.record(
                new CreateTestResultRequest(subject.id(), visit.id(), "Hemoglobin", "13.5", "g/dL", "12-16", false, null),
                coordinator.getUsername());
        assertEquals("RECORDED", recorded.status());

        TestResultResponse reviewed = testResultService.review(recorded.id(), investigator.getUsername());
        assertEquals("REVIEWED", reviewed.status());
        assertEquals(investigator.getUsername(), reviewed.reviewedByUsername());

        TestResult testResult = testResultService.findTestResult(recorded.id());
        byte[] content = "lab report bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", content);
        TestResultAttachmentResponse attachment = attachmentService.upload(testResult, file, coordinator.getUsername());

        try (InputStream downloaded = attachmentService.download(attachment.id())) {
            assertArrayEquals(content, downloaded.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> ("TestResult".equals(a.getEntityName()) && String.valueOf(recorded.id()).equals(a.getEntityId()))
                        || ("TestResultAttachment".equals(a.getEntityName()) && String.valueOf(attachment.id()).equals(a.getEntityId())))
                .count();
        assertTrue(auditCount >= 3, "expected CREATE + STATE_CHANGE (review) + CREATE (attachment) + ACCESS (download), got " + auditCount);
    }

    @Test
    void reportMildAdverseEvent_doesNotCreateTask() {
        User manager = createTestUser("safety-mgr-it2", Role.STUDY_MANAGER);
        User coordinator = createTestUser("safety-coord-it2", Role.SITE_COORDINATOR);

        SubjectResponse subject = setUpSubjectWithVisit("2", coordinator.getUsername(), manager.getUsername());

        long tasksBefore = taskRepository.count();
        adverseEventService.report(
                new ReportAdverseEventRequest(subject.id(), null, "Mild headache", "MILD"), coordinator.getUsername());
        long tasksAfter = taskRepository.count();

        assertEquals(0, tasksAfter - tasksBefore);
    }

    @Test
    void reportSevereAdverseEvent_createsRealEscalationTaskViaDrools() {
        User manager = createTestUser("safety-mgr-it3", Role.STUDY_MANAGER);
        User coordinator = createTestUser("safety-coord-it3", Role.SITE_COORDINATOR);

        SubjectResponse subject = setUpSubjectWithVisit("3", coordinator.getUsername(), manager.getUsername());

        long tasksBefore = taskRepository.count();
        AdverseEventResponse ae = adverseEventService.report(
                new ReportAdverseEventRequest(subject.id(), null, "Severe allergic reaction", "SEVERE"), coordinator.getUsername());
        long tasksAfter = taskRepository.count();

        assertEquals(1, tasksAfter - tasksBefore);
        Task task = taskRepository.findAll().stream()
                .filter(t -> "AdverseEvent".equals(t.getEntityName()) && ae.id().equals(t.getEntityId()))
                .findFirst()
                .orElseThrow();
        assertEquals("ADVERSE_EVENT_HIGH_SEVERITY", task.getEventCode());
        assertEquals(coordinator.getUsername(), task.getOwner().getUsername());
        assertEquals(manager.getUsername(), task.getEscalationTarget().getUsername());
    }

    @Test
    void adverseEventFullLifecycle_openToUnderReviewToResolved() {
        User manager = createTestUser("safety-mgr-it4", Role.STUDY_MANAGER);
        User coordinator = createTestUser("safety-coord-it4", Role.SITE_COORDINATOR);
        User investigator = createTestUser("safety-inv-it4", Role.INVESTIGATOR);

        SubjectResponse subject = setUpSubjectWithVisit("4", coordinator.getUsername(), manager.getUsername());

        AdverseEventResponse ae = adverseEventService.report(
                new ReportAdverseEventRequest(subject.id(), null, "Moderate nausea", "MODERATE"), coordinator.getUsername());
        assertEquals("OPEN", ae.status());

        assertThrows(InvalidAdverseEventTransitionException.class, () -> adverseEventService.resolve(
                ae.id(), new ResolveAdverseEventRequest("too early"), investigator.getUsername()));

        AdverseEventResponse underReview = adverseEventService.transition(
                ae.id(), new TransitionAdverseEventRequest("UNDER_REVIEW", "investigating"), investigator.getUsername());
        assertEquals("UNDER_REVIEW", underReview.status());

        AdverseEventResponse resolved = adverseEventService.resolve(
                ae.id(), new ResolveAdverseEventRequest("Resolved after monitoring"), investigator.getUsername());
        assertEquals("RESOLVED", resolved.status());
        assertEquals("Resolved after monitoring", resolved.resolutionNotes());

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "AdverseEvent".equals(a.getEntityName()) && String.valueOf(ae.id()).equals(a.getEntityId()))
                .count();
        assertTrue(auditCount >= 3, "expected CREATE + 2x STATE_CHANGE, got " + auditCount);
    }
}
