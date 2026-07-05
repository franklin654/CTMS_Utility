package com.ctms.ctms_backend.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.WithdrawSubjectRequest;
import com.ctms.ctms_backend.subject.service.SubjectLifecycleService;
import com.ctms.ctms_backend.subject.service.SubjectService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.dto.CreateAdHocVisitRequest;
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitCompletedRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitMissedRequest;
import com.ctms.ctms_backend.visit.dto.RescheduleVisitRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.dto.UpdateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.VisitResponse;
import com.ctms.ctms_backend.visit.dto.VisitTemplateResponse;
import com.ctms.ctms_backend.visit.service.VisitService;
import com.ctms.ctms_backend.visit.service.VisitTemplateService;
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
 * dedicated ctms_testdb), mirrors SubjectManagementIntegrationTest.
 */
@SpringBootTest
@Transactional
class VisitManagementIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SubjectService subjectService;
    @Autowired private SubjectLifecycleService subjectLifecycleService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
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

    private StudyResponse createStudy(String coordinatorUsername, String protocolId) {
        return studyService.createStudy(
                new CreateStudyRequest("Visit IT Trial", protocolId, "1.0", "PHASE_III", "Acme", null, null, null),
                coordinatorUsername);
    }

    private SiteResponse createSite(String coordinatorUsername, Long studyId, String siteCode) {
        return siteService.registerSite(
                new CreateSiteRequest(
                        studyId, siteCode, "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                coordinatorUsername);
    }

    private SubjectResponse enrollSubject(String coordinatorUsername, Long studyId, Long siteId, LocalDate screeningDate) {
        EnrollSubjectRequest req = new EnrollSubjectRequest(
                studyId, siteId, "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", "555-1234",
                "jane@example.com", "1 Main St", "John Doe", null, null, screeningDate, List.of());
        return subjectService.enrollSubject(req, coordinatorUsername);
    }

    @Test
    void enrollment_autoGeneratesVisitSchedule_anchoredToScreeningDate() {
        User coordinator = createTestUser("visit-coord-it1", Role.SITE_COORDINATOR);
        StudyResponse study = createStudy(coordinator.getUsername(), "VISIT-IT-PROTO-1");
        SiteResponse site = createSite(coordinator.getUsername(), study.id(), "VISIT-IT-SITE-001");

        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE"), coordinator.getUsername());
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Follow-up Visit", 2, 14, 2, 2, "Bloodwork", "REMOTE"), coordinator.getUsername());

        LocalDate screeningDate = LocalDate.of(2026, 1, 1);
        SubjectResponse subject = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);

        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        assertEquals(2, schedule.visits().size());
        assertEquals(screeningDate, schedule.visits().get(0).scheduledDate());
        assertEquals(screeningDate.plusDays(14), schedule.visits().get(1).scheduledDate());
        assertEquals("SCHEDULED", schedule.visits().get(0).status());
    }

    @Test
    void templateUpdate_propagatesToScheduledVisitsOnly_notCompleted() {
        User coordinator = createTestUser("visit-coord-it2", Role.SITE_COORDINATOR);
        StudyResponse study = createStudy(coordinator.getUsername(), "VISIT-IT-PROTO-2");
        SiteResponse site = createSite(coordinator.getUsername(), study.id(), "VISIT-IT-SITE-002");

        VisitTemplateResponse template = visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE"), coordinator.getUsername());

        LocalDate screeningDate = LocalDate.of(2026, 1, 1);
        SubjectResponse subjectA = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);
        SubjectResponse subjectB = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);

        // Complete subject B's visit before the template changes -- it must stay untouched.
        VisitResponse subjectBVisit = visitService.schedule(subjectB.id()).visits().get(0);
        visitService.markCompleted(subjectBVisit.id(), new MarkVisitCompletedRequest(screeningDate, null, "done"), coordinator.getUsername());

        visitTemplateService.update(template.id(), new UpdateVisitTemplateRequest(
                "Screening Visit (v2)", 1, 10, 1, 1, "Vitals + ECG", "ONSITE"), coordinator.getUsername());

        VisitResponse subjectAVisitAfter = visitService.schedule(subjectA.id()).visits().get(0);
        assertEquals("Screening Visit (v2)", subjectAVisitAfter.name());
        assertEquals(screeningDate.plusDays(10), subjectAVisitAfter.scheduledDate());

        VisitResponse subjectBVisitAfter = visitService.schedule(subjectB.id()).visits().get(0);
        assertEquals("Screening Visit", subjectBVisitAfter.name());
        assertEquals(screeningDate, subjectBVisitAfter.scheduledDate());
        assertEquals("COMPLETED", subjectBVisitAfter.status());
    }

    @Test
    void reschedule_createsLinkedTrail_andComplianceRateReflectsMixedOutcomes() {
        User coordinator = createTestUser("visit-coord-it3", Role.SITE_COORDINATOR);
        StudyResponse study = createStudy(coordinator.getUsername(), "VISIT-IT-PROTO-3");
        SiteResponse site = createSite(coordinator.getUsername(), study.id(), "VISIT-IT-SITE-003");

        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Visit 1", 1, 5, 1, 1, "Vitals", "ONSITE"), coordinator.getUsername());
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Visit 2", 2, 15, 1, 1, "Bloodwork", "ONSITE"), coordinator.getUsername());
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Visit 3", 3, 60, 1, 1, "Follow-up", "REMOTE"), coordinator.getUsername());

        LocalDate screeningDate = LocalDate.now().minusDays(30);
        SubjectResponse subject = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);

        List<VisitResponse> visits = visitService.schedule(subject.id()).visits();
        VisitResponse visit1 = visits.get(0);
        VisitResponse visit2 = visits.get(1);
        VisitResponse visit3 = visits.get(2);

        visitService.markCompleted(visit1.id(), new MarkVisitCompletedRequest(visit1.scheduledDate(), null, "done"), coordinator.getUsername());
        visitService.markMissed(visit2.id(), new MarkVisitMissedRequest("Subject unreachable"), coordinator.getUsername());

        SubjectVisitScheduleResponse midSchedule = visitService.schedule(subject.id());
        assertEquals(0.5, midSchedule.complianceRate());

        VisitResponse rescheduled = visitService.reschedule(visit3.id(),
                new RescheduleVisitRequest(LocalDate.now().plusDays(65), "Rescheduling at subject's request"));
        assertEquals(visit3.id(), rescheduled.rescheduledFromVisitId());
        assertEquals("SCHEDULED", rescheduled.status());

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "Visit".equals(a.getEntityName())
                        && (String.valueOf(visit1.id()).equals(a.getEntityId())
                                || String.valueOf(visit2.id()).equals(a.getEntityId())
                                || String.valueOf(visit3.id()).equals(a.getEntityId())
                                || String.valueOf(rescheduled.id()).equals(a.getEntityId())))
                .count();
        assertTrue(auditCount >= 6, "expected >=6 Visit audit rows (3 CREATE at scheduling + complete + miss + reschedule pair), got " + auditCount);
    }

    @Test
    void newTemplate_backfillsActiveSubjects_butNotWithdrawn() {
        User coordinator = createTestUser("visit-coord-it4", Role.SITE_COORDINATOR);
        StudyResponse study = createStudy(coordinator.getUsername(), "VISIT-IT-PROTO-4");
        SiteResponse site = createSite(coordinator.getUsername(), study.id(), "VISIT-IT-SITE-004");

        LocalDate screeningDate = LocalDate.of(2026, 1, 1);
        SubjectResponse activeSubject = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);
        SubjectResponse withdrawnSubject = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);
        subjectLifecycleService.withdraw(
                withdrawnSubject.id(), new WithdrawSubjectRequest("lost to follow-up"), coordinator.getUsername());

        // No templates existed at enrollment time -- both subjects start with an empty schedule.
        assertEquals(0, visitService.schedule(activeSubject.id()).visits().size());
        assertEquals(0, visitService.schedule(withdrawnSubject.id()).visits().size());

        // Adding a template *after* enrollment should backfill the still-active subject only.
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Late-Added Visit", 1, 7, 1, 1, "Vitals", "ONSITE"), coordinator.getUsername());

        List<VisitResponse> activeVisits = visitService.schedule(activeSubject.id()).visits();
        assertEquals(1, activeVisits.size());
        assertEquals(screeningDate.plusDays(7), activeVisits.get(0).scheduledDate());

        assertEquals(0, visitService.schedule(withdrawnSubject.id()).visits().size());
    }

    @Test
    void adHocVisit_createdWithNullTemplate_excludedFromComplianceRate() {
        User coordinator = createTestUser("visit-coord-it5", Role.SITE_COORDINATOR);
        StudyResponse study = createStudy(coordinator.getUsername(), "VISIT-IT-PROTO-5");
        SiteResponse site = createSite(coordinator.getUsername(), study.id(), "VISIT-IT-SITE-005");

        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE"), coordinator.getUsername());

        LocalDate screeningDate = LocalDate.now().minusDays(10);
        SubjectResponse subject = enrollSubject(coordinator.getUsername(), study.id(), site.id(), screeningDate);

        VisitResponse protocolVisit = visitService.schedule(subject.id()).visits().get(0);
        visitService.markCompleted(protocolVisit.id(), new MarkVisitCompletedRequest(screeningDate, null, "done"), coordinator.getUsername());

        VisitResponse adHocVisit = visitService.scheduleAdHoc(
                subject.id(),
                new CreateAdHocVisitRequest("AE Follow-up", LocalDate.now().minusDays(1), "ONSITE", "Vitals recheck", "Reported mild AE"),
                coordinator.getUsername());
        assertTrue(adHocVisit.adHoc());
        assertEquals(null, adHocVisit.visitTemplateId());

        // Mark the ad-hoc visit missed -- it must NOT drag down the protocol compliance rate.
        visitService.markMissed(adHocVisit.id(), new MarkVisitMissedRequest("Subject rescheduled"), coordinator.getUsername());

        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        assertEquals(2, schedule.visits().size());
        assertEquals(1.0, schedule.complianceRate());
    }
}
