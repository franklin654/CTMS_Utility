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
import com.ctms.ctms_backend.budget.dto.BudgetLineItemRequest;
import com.ctms.ctms_backend.budget.dto.CreateBudgetRequest;
import com.ctms.ctms_backend.budget.service.BudgetService;
import com.ctms.ctms_backend.deviation.dto.ProtocolDeviationResponse;
import com.ctms.ctms_backend.deviation.dto.ReportProtocolDeviationRequest;
import com.ctms.ctms_backend.deviation.service.ProtocolDeviationService;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.exception.MissingConsentException;
import com.ctms.ctms_backend.payment.dto.HoldPaymentRequest;
import com.ctms.ctms_backend.payment.dto.PaymentResponse;
import com.ctms.ctms_backend.payment.dto.ReleasePaymentRequest;
import com.ctms.ctms_backend.payment.entity.PaymentStatus;
import com.ctms.ctms_backend.payment.service.PaymentService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest;
import com.ctms.ctms_backend.site.entity.ChecklistItemType;
import com.ctms.ctms_backend.site.service.SiteActivationService;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.dto.TransitionStudyRequest;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.service.SubjectService;
import com.ctms.ctms_backend.task.entity.Task;
import com.ctms.ctms_backend.task.repository.TaskRepository;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Phase 13 regression finding: no test previously exercised a single continuous chain spanning
 * most/all feature areas together (each phase's integration test re-derives its own throwaway
 * Study/Site/Subject scaffolding and asserts only on its own area). This test chains: Study
 * activation -> Site activation (with auto-created CRA-assignment task) -> Subject enrollment ->
 * consent-gated Visit completion -> Protocol Deviation -> Adverse Event (Drools escalation task +
 * e-signed resolution) -> Budget/Payment (Drools-generated, e-signed release) -> a final
 * Traceability Report proving the whole chain is reconstructible from one entity's audit trail. */
@SpringBootTest
@Transactional
class CrossPhaseEndToEndIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SiteActivationService siteActivationService;
    @Autowired private SubjectService subjectService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private DocumentService documentService;
    @Autowired private ProtocolDeviationService protocolDeviationService;
    @Autowired private AdverseEventService adverseEventService;
    @Autowired private BudgetService budgetService;
    @Autowired private PaymentService paymentService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private AuditLogController auditLogController;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String username, String roleCode) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@ctms.local");
        user.setFullName("Cross-Phase E2E Test User");
        user.setPasswordHash(passwordEncoder.encode("Integration!Test2026Pass"));
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        user.setRoles(new HashSet<>(List.of(role)));
        return userRepository.save(user);
    }

    private TraceabilityResponse traceabilityAsAuditor(String entityName, String entityId) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "auditor", null, List.of(new SimpleGrantedAuthority("ROLE_" + Role.ADMIN))));
        return auditLogController.traceability(entityName, entityId);
    }

    @Test
    void fullLifecycle_studyThroughTraceability_allPhasesInterlock() {
        // Ensure an ADMIN exists so site activation's no-CRA-assigned task has somewhere to land.
        User admin = createUser("e2e-admin", Role.ADMIN);
        User manager = createUser("e2e-mgr", Role.STUDY_MANAGER);
        User coordinator = createUser("e2e-coord", Role.SITE_COORDINATOR);
        User investigator = createUser("e2e-investigator", Role.INVESTIGATOR);
        User finance = createUser("e2e-finance", Role.FINANCE_MANAGER);

        // --- Study: create, then DRAFT -> ACTIVE -> CONDUCT ---
        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Cross-Phase E2E Trial", "E2E-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        studyService.transition(study.id(), new TransitionStudyRequest("ACTIVE", "Protocol approved"), manager.getUsername());
        StudyResponse conductStudy = studyService.transition(
                study.id(), new TransitionStudyRequest("CONDUCT", "First site activating"), manager.getUsername());
        assertEquals("CONDUCT", conductStudy.status());

        // --- Site: register, complete checklist (no CRA assigned -> auto-creates a task), auto-activates ---
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "E2E-SITE-1", "E2E Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        for (ChecklistItemType itemType : ChecklistItemType.values()) {
            siteActivationService.updateChecklistItem(
                    site.id(), itemType.name(), new UpdateChecklistItemRequest("COMPLETE", null, "done"), manager.getUsername());
        }
        SiteResponse activeSite = siteService.get(site.id());
        assertEquals("ACTIVE", activeSite.status());

        boolean craAssignmentTaskCreated = taskRepository.findAll().stream()
                .anyMatch(t -> "SITE_ACTIVATED".equals(t.getEventCode()) && "Site".equals(t.getEntityName())
                        && site.id().equals(t.getEntityId()));
        assertTrue(craAssignmentTaskCreated, "expected an auto-created CRA-assignment task for the newly ACTIVE site");

        // --- Visit template + Subject enrollment ---
        visitTemplateService.create(
                new CreateVisitTemplateRequest(study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null),
                manager.getUsername());
        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Alex", "Rivera", LocalDate.of(1982, 6, 15), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        VisitResponse visit = schedule.visits().get(0);

        // --- Consent gate: blocked, then unblocked after a subject-linked upload ---
        assertThrows(MissingConsentException.class, () -> visitService.markCompleted(
                visit.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "attempt without consent"), coordinator.getUsername()));

        documentService.createDocument(
                "Consent Form", "INFORMED_CONSENT", study.id(), subject.id(), coordinator.getUsername(),
                new MockMultipartFile("file", "consent.pdf", "application/pdf", "content".getBytes()));

        VisitResponse completedVisit = visitService.markCompleted(
                visit.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername());
        assertEquals("COMPLETED", completedVisit.status());

        // --- Protocol Deviation (log-only) ---
        ProtocolDeviationResponse deviation = protocolDeviationService.report(
                new ReportProtocolDeviationRequest(subject.id(), "Visit conducted 1 day outside window", "MINOR", LocalDate.now()),
                coordinator.getUsername());
        assertEquals("MINOR", deviation.severity());

        // --- Adverse Event: SEVERE -> Drools escalation task -> resolve (wrong password rejected, then signed) ---
        AdverseEventResponse ae = adverseEventService.report(
                new ReportAdverseEventRequest(subject.id(), visit.id(), "Severe injection-site reaction", "SEVERE"),
                coordinator.getUsername());
        assertEquals("OPEN", ae.status());

        boolean aeEscalationTaskCreated = taskRepository.findAll().stream()
                .anyMatch(t -> "ADVERSE_EVENT_HIGH_SEVERITY".equals(t.getEventCode())
                        && "AdverseEvent".equals(t.getEntityName()) && ae.id().equals(t.getEntityId()));
        assertTrue(aeEscalationTaskCreated, "expected an auto-created escalation task for the SEVERE adverse event");

        adverseEventService.transition(
                ae.id(), new TransitionAdverseEventRequest("UNDER_REVIEW", "investigating"), investigator.getUsername());

        assertThrows(InvalidCredentialsException.class, () -> adverseEventService.resolve(
                ae.id(), new ResolveAdverseEventRequest("Resolved", "wrong-password"), investigator.getUsername()));

        AdverseEventResponse resolvedAe = adverseEventService.resolve(
                ae.id(), new ResolveAdverseEventRequest("Resolved after monitoring", "Integration!Test2026Pass"),
                investigator.getUsername());
        assertEquals("RESOLVED", resolvedAe.status());

        // --- Budget + Drools-generated Payment (from the completed visit) -> hold -> signed release ---
        budgetService.create(
                new CreateBudgetRequest(study.id(), List.of(new BudgetLineItemRequest("SITE_PAYMENTS", new BigDecimal("500.00"), "USD"))),
                finance.getUsername());

        List<PaymentResponse> payments = paymentService.list(study.id(), null, null, null, PageRequest.of(0, 10)).getContent();
        PaymentResponse triggeredPayment = payments.stream()
                .filter(p -> "Visit".equals(p.triggerEntityName()) && visit.id().equals(p.triggerEntityId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a Drools-generated payment triggered by the completed visit"));
        assertEquals(PaymentStatus.PENDING.name(), triggeredPayment.status());

        paymentService.hold(triggeredPayment.id(), new HoldPaymentRequest("awaiting site invoice confirmation"), finance.getUsername());

        assertThrows(InvalidCredentialsException.class, () -> paymentService.release(
                triggeredPayment.id(), new ReleasePaymentRequest("release", "wrong-password"), finance.getUsername()));

        PaymentResponse releasedPayment = paymentService.release(
                triggeredPayment.id(), new ReleasePaymentRequest("Reviewed and approved", "Integration!Test2026Pass"), finance.getUsername());
        assertEquals("RELEASED", releasedPayment.status());

        // --- Traceability: the whole chain reconstructible from one entity's audit trail ---
        TraceabilityResponse aeTrace = traceabilityAsAuditor("AdverseEvent", String.valueOf(ae.id()));
        assertFalse(aeTrace.auditTrail().isEmpty(), "adverse event should have a real audit trail");
        assertFalse(aeTrace.signatures().isEmpty(), "adverse event resolution should have a real e-signature");

        TraceabilityResponse paymentTrace = traceabilityAsAuditor("Payment", String.valueOf(triggeredPayment.id()));
        assertFalse(paymentTrace.auditTrail().isEmpty(), "payment should have a real audit trail");
        assertFalse(paymentTrace.signatures().isEmpty(), "payment release should have a real e-signature");

        TraceabilityResponse visitTrace = traceabilityAsAuditor("Visit", String.valueOf(visit.id()));
        assertFalse(visitTrace.auditTrail().isEmpty(), "visit completion should have a real audit trail");
    }
}
