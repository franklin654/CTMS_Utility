package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.budget.dto.BudgetLineItemRequest;
import com.ctms.ctms_backend.budget.dto.BudgetVersionResponse;
import com.ctms.ctms_backend.budget.dto.CreateBudgetRequest;
import com.ctms.ctms_backend.budget.dto.CreateBudgetVersionRequest;
import com.ctms.ctms_backend.budget.service.BudgetService;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.esignature.ESignatureRepository;
import com.ctms.ctms_backend.payment.dto.HoldPaymentRequest;
import com.ctms.ctms_backend.payment.dto.PaymentResponse;
import com.ctms.ctms_backend.payment.dto.ReleasePaymentRequest;
import com.ctms.ctms_backend.payment.entity.PaymentStatus;
import com.ctms.ctms_backend.payment.repository.PaymentRepository;
import com.ctms.ctms_backend.payment.service.PaymentService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteActivationService;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), mirrors ClinicalSafetyIntegrationTest / MonitoringAndReportingIntegrationTest. */
@SpringBootTest
@Transactional
class FinancialManagementIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SiteActivationService siteActivationService;
    @Autowired private SubjectService subjectService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private DocumentService documentService;
    @Autowired private BudgetService budgetService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ESignatureRepository eSignatureRepository;
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

    @Test
    void fullFinancialLifecycle_budgetVersioningAndPaymentHoldRelease() {
        User manager = createTestUser("fin-mgr-it1", Role.STUDY_MANAGER);
        User coordinator = createTestUser("fin-coord-it1", Role.SITE_COORDINATOR);
        User finance = createTestUser("fin-finance-it1", Role.FINANCE_MANAGER);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Financial IT Trial", "FIN-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "FIN-IT-SITE-1", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        visitTemplateService.create(new CreateVisitTemplateRequest(
                study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null), manager.getUsername());

        // 1. Create budget v1 (no reason required for the first version).
        BudgetVersionResponse v1 = budgetService.create(
                new CreateBudgetRequest(study.id(), List.of(new BudgetLineItemRequest("MONITORING", new BigDecimal("10000.00"), "USD"))),
                finance.getUsername());
        assertEquals(1, v1.versionNumber());
        assertEquals("CURRENT", v1.status());

        // 2. Complete a real visit -> confirm a real VISIT_COMPLETED payment auto-generates.
        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());
        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        VisitResponse visit = schedule.visits().get(0);

        // Epic 11 Story 01 consent gate -- markCompleted requires a CURRENT INFORMED_CONSENT
        // document on file for this subject.
        documentService.createDocument(
                "Consent Form", "INFORMED_CONSENT", study.id(), subject.id(), coordinator.getUsername(),
                new MockMultipartFile("file", "consent.pdf", "application/pdf", "content".getBytes()));

        long paymentsBefore = paymentRepository.count();
        visitService.markCompleted(visit.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername());
        long paymentsAfter = paymentRepository.count();
        assertEquals(1, paymentsAfter - paymentsBefore);

        Page<PaymentResponse> payments = paymentService.list(study.id(), null, null, null, PageRequest.of(0, 20));
        PaymentResponse payment = payments.getContent().stream()
                .filter(p -> "VISIT_COMPLETED".equals(p.eventCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("PENDING", payment.status());
        assertEquals(0, new BigDecimal("500.00").compareTo(payment.amount()));

        // 3. Hold it.
        PaymentResponse held = paymentService.hold(payment.id(), new HoldPaymentRequest("Budget review pending"), finance.getUsername());
        assertEquals("ON_HOLD", held.status());

        // 4. Attempt release with wrong password -> rejected, still ON_HOLD.
        assertThrows(InvalidCredentialsException.class, () -> paymentService.release(
                payment.id(), new ReleasePaymentRequest("release reason", "wrong-password"), finance.getUsername()));
        PaymentResponse stillHeld = paymentService.get(payment.id());
        assertEquals("ON_HOLD", stillHeld.status());

        // 5. Release with correct password -> RELEASED, real ESignature row created.
        long signaturesBefore = eSignatureRepository.count();
        PaymentResponse released = paymentService.release(
                payment.id(), new ReleasePaymentRequest("Reviewed and approved", "Integration!Test2026Pass"), finance.getUsername());
        assertEquals("RELEASED", released.status());
        assertEquals(1, eSignatureRepository.count() - signaturesBefore);

        // 6. Create budget v2 with a reason -> confirm v1 is SUPERSEDED and read-only.
        BudgetVersionResponse v2 = budgetService.createNewVersion(
                study.id(),
                new CreateBudgetVersionRequest(
                        List.of(new BudgetLineItemRequest("MONITORING", new BigDecimal("15000.00"), "USD")),
                        "Increased monitoring budget after site feedback"),
                finance.getUsername());
        assertEquals(2, v2.versionNumber());
        BudgetVersionResponse v1AfterSupersede = budgetService.getVersion(study.id(), 1);
        assertEquals("SUPERSEDED", v1AfterSupersede.status());

        // 7. Confirm actual/variance reflects the released payment correctly.
        BudgetVersionResponse current = budgetService.getCurrentVersion(study.id());
        assertEquals(1, current.lineItems().size());
        assertEquals(0, new BigDecimal("500.00").compareTo(current.lineItems().get(0).actualAmount()));
        assertEquals(0, new BigDecimal("14500.00").compareTo(current.lineItems().get(0).variance()));
    }

    @Test
    void siteActivation_generatesExpectedPayment() {
        User manager = createTestUser("fin-mgr-it2", Role.STUDY_MANAGER);
        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Financial IT Trial 2", "FIN-IT-PROTO-2", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "FIN-IT-SITE-2", "IT Test Hospital 2", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());

        long paymentsBefore = paymentRepository.count();
        completeAllChecklistItems(site.id(), manager.getUsername());
        siteActivationService.attemptActivation(
                site.id(), new com.ctms.ctms_backend.site.dto.AttemptActivationRequest("Integration!Test2026Pass", "all prerequisites met"),
                manager.getUsername());
        long paymentsAfter = paymentRepository.count();
        assertEquals(1, paymentsAfter - paymentsBefore);

        Page<PaymentResponse> payments = paymentService.list(study.id(), null, null, null, PageRequest.of(0, 20));
        PaymentResponse payment = payments.getContent().stream()
                .filter(p -> "SITE_ACTIVATED".equals(p.eventCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("2000.00").compareTo(payment.amount()));
    }

    private void completeAllChecklistItems(Long siteId, String actorUsername) {
        for (String itemType : List.of(
                "FEASIBILITY_COMPLETION", "IRB_EC_APPROVAL", "CONTRACT_COMPLETION",
                "ESSENTIAL_DOCUMENTS_SUBMISSION", "SITE_INITIATION_VISIT")) {
            siteActivationService.updateChecklistItem(
                    siteId, itemType, new com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest("COMPLETE", null, "done"), actorUsername);
        }
    }
}
