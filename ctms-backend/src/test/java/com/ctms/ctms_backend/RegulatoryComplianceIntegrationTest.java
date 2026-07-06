package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.adverseevent.dto.ReportAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.ResolveAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.TransitionAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.service.AdverseEventService;
import com.ctms.ctms_backend.audit.AuditLogController;
import com.ctms.ctms_backend.audit.TraceabilityResponse;
import com.ctms.ctms_backend.deviation.dto.ProtocolDeviationResponse;
import com.ctms.ctms_backend.deviation.dto.ReportProtocolDeviationRequest;
import com.ctms.ctms_backend.deviation.service.ProtocolDeviationService;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.exception.MissingConsentException;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.dto.ActivationAttemptResponse;
import com.ctms.ctms_backend.site.dto.AttemptActivationRequest;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.entity.ChecklistItemStatus;
import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import com.ctms.ctms_backend.site.repository.SiteActivationChecklistItemRepository;
import com.ctms.ctms_backend.site.service.SiteActivationService;
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
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitCompletedRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.dto.VisitResponse;
import com.ctms.ctms_backend.visit.service.VisitService;
import com.ctms.ctms_backend.visit.service.VisitTemplateService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), mirrors PatientPortalIntegrationTest / SystemConfigurationIntegrationTest. */
@SpringBootTest
@Transactional
class RegulatoryComplianceIntegrationTest {

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SiteActivationService siteActivationService;
    @Autowired private SiteActivationChecklistItemRepository checklistRepository;
    @Autowired private SubjectService subjectService;
    @Autowired private SubjectLifecycleService subjectLifecycleService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private DocumentService documentService;
    @Autowired private AdverseEventService adverseEventService;
    @Autowired private ProtocolDeviationService protocolDeviationService;
    @Autowired private AuditLogController auditLogController;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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

    private void uploadConsent(Long studyId, Long subjectId, String actorUsername) {
        documentService.createDocument(
                "Consent Form", "INFORMED_CONSENT", studyId, subjectId, actorUsername,
                new MockMultipartFile("file", "consent.pdf", "application/pdf", "content".getBytes()));
    }

    /** AuditLogController is @PreAuthorize-guarded, so calling it directly (bypassing the real
     * HTTP filter chain) needs a SecurityContext populated, same as the JWT filter would do on a
     * real request. */
    private TraceabilityResponse traceabilityAsAuditor(String entityName, String entityId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        "auditor", null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + Role.ADMIN))));
        return auditLogController.traceability(entityName, entityId);
    }

    @Test
    void consentGate_blocksPerSubjectNotPerStudy_thenUnblocksAfterUpload() {
        User manager = createTestUser("rc-mgr-it1", Role.STUDY_MANAGER);
        User coordinator = createTestUser("rc-coord-it1", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Regulatory Compliance IT Trial 1", "RC-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "RC-IT-SITE-1", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        visitTemplateService.create(
                new CreateVisitTemplateRequest(study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null),
                manager.getUsername());

        SubjectResponse subjectA = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Alice", "Alpha", LocalDate.of(1985, 5, 5), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());
        SubjectResponse subjectB = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Bob", "Beta", LocalDate.of(1988, 8, 8), "MALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        VisitResponse visitA = visitService.schedule(subjectA.id()).visits().get(0);
        VisitResponse visitB = visitService.schedule(subjectB.id()).visits().get(0);

        // Neither subject has consent on file yet -- both blocked.
        assertThrows(MissingConsentException.class, () -> visitService.markCompleted(
                visitA.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername()));

        // Upload consent for A only -- B must remain blocked despite sharing the same study.
        uploadConsent(study.id(), subjectA.id(), coordinator.getUsername());
        VisitResponse completedA = visitService.markCompleted(
                visitA.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername());
        assertEquals("COMPLETED", completedA.status());

        assertThrows(MissingConsentException.class, () -> visitService.markCompleted(
                visitB.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername()));

        uploadConsent(study.id(), subjectB.id(), coordinator.getUsername());
        VisitResponse completedB = visitService.markCompleted(
                visitB.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername());
        assertEquals("COMPLETED", completedB.status());
    }

    @Test
    void protocolDeviation_reportedAndAudited() {
        User manager = createTestUser("rc-mgr-it2", Role.STUDY_MANAGER);
        User coordinator = createTestUser("rc-coord-it2", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Regulatory Compliance IT Trial 2", "RC-IT-PROTO-2", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "RC-IT-SITE-2", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Carl", "Gamma", LocalDate.of(1975, 3, 3), "MALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        ProtocolDeviationResponse deviation = protocolDeviationService.report(
                new ReportProtocolDeviationRequest(subject.id(), "Visit window missed by 3 days", "MINOR", LocalDate.now()),
                coordinator.getUsername());
        assertEquals("MINOR", deviation.severity());

        List<ProtocolDeviationResponse> list = protocolDeviationService.list(subject.id());
        assertEquals(1, list.size());

        TraceabilityResponse trace = traceabilityAsAuditor("ProtocolDeviation", String.valueOf(deviation.id()));
        assertFalse(trace.auditTrail().isEmpty());
    }

    @Test
    void subjectWithdrawal_requiresCorrectPassword_wrongPasswordLeavesUntouched() {
        User manager = createTestUser("rc-mgr-it3", Role.STUDY_MANAGER);
        User coordinator = createTestUser("rc-coord-it3", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Regulatory Compliance IT Trial 3", "RC-IT-PROTO-3", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "RC-IT-SITE-3", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Dana", "Delta", LocalDate.of(1992, 2, 2), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        assertThrows(InvalidCredentialsException.class, () -> subjectLifecycleService.withdraw(
                subject.id(), new WithdrawSubjectRequest("lost to follow-up", "wrong-password"), coordinator.getUsername()));
        assertEquals("SCREENED", subjectService.get(subject.id()).status());

        SubjectResponse withdrawn = subjectLifecycleService.withdraw(
                subject.id(), new WithdrawSubjectRequest("lost to follow-up", "Integration!Test2026Pass"), coordinator.getUsername());
        assertEquals("WITHDRAWN", withdrawn.status());

        TraceabilityResponse trace = traceabilityAsAuditor("Subject", String.valueOf(subject.id()));
        assertFalse(trace.signatures().isEmpty());
    }

    @Test
    void adverseEventResolution_requiresCorrectPassword_wrongPasswordLeavesUntouched() {
        User manager = createTestUser("rc-mgr-it4", Role.STUDY_MANAGER);
        User coordinator = createTestUser("rc-coord-it4", Role.SITE_COORDINATOR);
        User investigator = createTestUser("rc-inv-it4", Role.INVESTIGATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Regulatory Compliance IT Trial 4", "RC-IT-PROTO-4", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "RC-IT-SITE-4", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Eli", "Epsilon", LocalDate.of(1980, 4, 4), "MALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        AdverseEventResponse ae = adverseEventService.report(
                new ReportAdverseEventRequest(subject.id(), null, "Mild rash", "MILD"), coordinator.getUsername());
        adverseEventService.transition(ae.id(), new TransitionAdverseEventRequest("UNDER_REVIEW", "reviewing"), investigator.getUsername());

        assertThrows(InvalidCredentialsException.class, () -> adverseEventService.resolve(
                ae.id(), new ResolveAdverseEventRequest("Resolved", "wrong-password"), investigator.getUsername()));
        assertEquals("UNDER_REVIEW", adverseEventService.get(ae.id()).status());

        AdverseEventResponse resolved = adverseEventService.resolve(
                ae.id(), new ResolveAdverseEventRequest("Resolved with rest", "Integration!Test2026Pass"), investigator.getUsername());
        assertEquals("RESOLVED", resolved.status());

        TraceabilityResponse trace = traceabilityAsAuditor("AdverseEvent", String.valueOf(ae.id()));
        assertFalse(trace.signatures().isEmpty());
    }

    @Test
    void siteActivation_requiresCorrectPassword_wrongPasswordLeavesUntouched() {
        User manager = createTestUser("rc-mgr-it5", Role.STUDY_MANAGER);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Regulatory Compliance IT Trial 5", "RC-IT-PROTO-5", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "RC-IT-SITE-5", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());

        // Complete every checklist item directly via the repository (bypassing
        // updateChecklistItem's own auto-promotion check) so the site is genuinely
        // PENDING_ACTIVATION with a fully-complete checklist -- the one real path that reaches
        // attemptActivation's signed promotion branch. In normal usage, completing the LAST item
        // via updateChecklistItem auto-promotes silently without e-signature -- a known,
        // deliberately out-of-scope gap documented in the phase report.
        for (SiteActivationChecklistItem item : checklistRepository.findBySiteIdOrderByItemType(site.id())) {
            item.setStatus(ChecklistItemStatus.COMPLETE);
            item.setCompletedDate(LocalDate.now());
            checklistRepository.save(item);
        }

        assertThrows(InvalidCredentialsException.class, () -> siteActivationService.attemptActivation(
                site.id(), new AttemptActivationRequest("wrong-password", "attempting"), manager.getUsername()));
        assertEquals("PENDING_ACTIVATION", siteService.get(site.id()).status());

        ActivationAttemptResponse activated = siteActivationService.attemptActivation(
                site.id(), new AttemptActivationRequest("Integration!Test2026Pass", "all prerequisites met"), manager.getUsername());
        assertTrue(activated.activated());
        assertEquals("ACTIVE", siteService.get(site.id()).status());

        TraceabilityResponse trace = traceabilityAsAuditor("Site", String.valueOf(site.id()));
        assertFalse(trace.signatures().isEmpty());
    }
}
