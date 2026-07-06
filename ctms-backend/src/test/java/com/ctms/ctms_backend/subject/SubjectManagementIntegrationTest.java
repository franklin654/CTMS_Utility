package com.ctms.ctms_backend.subject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.notification.NotificationRepository;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.CreateEligibilityCriterionRequest;
import com.ctms.ctms_backend.subject.dto.EligibilityAnswerRequest;
import com.ctms.ctms_backend.subject.dto.EligibilityCriterionResponse;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.TransitionSubjectRequest;
import com.ctms.ctms_backend.subject.dto.WithdrawSubjectRequest;
import com.ctms.ctms_backend.subject.exception.EligibilityFailedException;
import com.ctms.ctms_backend.subject.service.EligibilityCriterionService;
import com.ctms.ctms_backend.subject.service.SubjectLifecycleService;
import com.ctms.ctms_backend.subject.service.SubjectService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb) with REAL Drools evaluation (not mocked) -- mirrors
 * StudyManagementIntegrationTest / SiteManagementIntegrationTest.
 */
@SpringBootTest
@Transactional
class SubjectManagementIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private EligibilityCriterionService criterionService;
    @Autowired private SubjectService subjectService;
    @Autowired private SubjectLifecycleService subjectLifecycleService;
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

    @Test
    void fullLifecycle_enrollmentThroughCompletion_realDroolsEvaluation() {
        User coordinator = createTestUser("subj-coord-it", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Subject IT Trial", "SUBJ-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                coordinator.getUsername());

        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "SUBJ-IT-SITE-001", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                coordinator.getUsername());

        EligibilityCriterionResponse inclusion = criterionService.create(
                new CreateEligibilityCriterionRequest(study.id(), "Age >= 18", "INCLUSION"));
        EligibilityCriterionResponse exclusion = criterionService.create(
                new CreateEligibilityCriterionRequest(study.id(), "Pregnant", "EXCLUSION"));

        // Enrollment blocked -- inclusion criterion unmet (real Drools evaluation, not mocked).
        EnrollSubjectRequest failingReq = new EnrollSubjectRequest(
                study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", "555-1234",
                "jane@example.com", "1 Main St", "John Doe", "notes", "medical history", LocalDate.now(),
                List.of(new EligibilityAnswerRequest(inclusion.id(), false), new EligibilityAnswerRequest(exclusion.id(), false)));
        EligibilityFailedException ex = assertThrows(EligibilityFailedException.class,
                () -> subjectService.enrollSubject(failingReq, coordinator.getUsername()));
        assertTrue(ex.getViolations().stream().anyMatch(v -> v.contains("Age >= 18")));

        // Enrollment succeeds -- both criteria satisfied.
        EnrollSubjectRequest passingReq = new EnrollSubjectRequest(
                study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", "555-1234",
                "jane@example.com", "1 Main St", "John Doe", "notes", "medical history", LocalDate.now(),
                List.of(new EligibilityAnswerRequest(inclusion.id(), true), new EligibilityAnswerRequest(exclusion.id(), false)));
        SubjectResponse enrolled = subjectService.enrollSubject(passingReq, coordinator.getUsername());
        assertEquals("SCREENED", enrolled.status());
        assertTrue(enrolled.subjectCode().startsWith("SUBJ-"));

        // Full lifecycle.
        SubjectResponse afterEnrolled = subjectLifecycleService.transition(
                enrolled.id(), new TransitionSubjectRequest("ENROLLED", "consent signed"), coordinator.getUsername());
        assertEquals("ENROLLED", afterEnrolled.status());

        SubjectResponse afterTreatment = subjectLifecycleService.transition(
                enrolled.id(), new TransitionSubjectRequest("IN_TREATMENT", "treatment started"), coordinator.getUsername());
        assertEquals("IN_TREATMENT", afterTreatment.status());

        SubjectResponse afterCompleted = subjectLifecycleService.transition(
                enrolled.id(), new TransitionSubjectRequest("COMPLETED", "treatment finished"), coordinator.getUsername());
        assertEquals("COMPLETED", afterCompleted.status());

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "Subject".equals(a.getEntityName()) && String.valueOf(enrolled.id()).equals(a.getEntityId()))
                .count();
        assertTrue(auditCount >= 4, "expected CREATE + 3x STATE_CHANGE, got " + auditCount);

        long notificationCount = notificationRepository.findAll().stream()
                .filter(n -> "SUBJECT_STATE_CHANGE".equals(n.getType()))
                .count();
        assertTrue(notificationCount >= 3, "expected 3 lifecycle notifications, got " + notificationCount);
    }

    @Test
    void withdrawal_fromEnrolled_succeedsWithReasonCode() {
        User coordinator = createTestUser("subj-coord-it2", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Withdrawal IT Trial", "SUBJ-IT-PROTO-2", "1.0", "PHASE_II", "Acme", null, null, null),
                coordinator.getUsername());

        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "SUBJ-IT-SITE-002", "IT Test Hospital 2", "2 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                coordinator.getUsername());

        EnrollSubjectRequest req = new EnrollSubjectRequest(
                study.id(), site.id(), "John", "Roe", LocalDate.of(1985, 5, 5), "MALE", "555-5678",
                "john@example.com", "2 Main St", "Jane Roe", null, null, LocalDate.now(), List.of());
        SubjectResponse enrolled = subjectService.enrollSubject(req, coordinator.getUsername());

        SubjectResponse afterEnrolled = subjectLifecycleService.transition(
                enrolled.id(), new TransitionSubjectRequest("ENROLLED", "consent signed"), coordinator.getUsername());
        assertEquals("ENROLLED", afterEnrolled.status());

        SubjectResponse withdrawn = subjectLifecycleService.withdraw(
                enrolled.id(), new WithdrawSubjectRequest("lost to follow-up", "Integration!Test2026Pass"), coordinator.getUsername());
        assertEquals("WITHDRAWN", withdrawn.status());
    }
}
