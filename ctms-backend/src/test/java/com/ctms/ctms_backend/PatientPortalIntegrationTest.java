package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.document.DocumentResponse;
import com.ctms.ctms_backend.patientportal.controller.PatientAdverseEventController;
import com.ctms.ctms_backend.patientportal.controller.PatientDocumentController;
import com.ctms.ctms_backend.patientportal.controller.PatientProfileController;
import com.ctms.ctms_backend.patientportal.controller.PatientVisitController;
import com.ctms.ctms_backend.patientportal.dto.PatientReportAdverseEventRequest;
import com.ctms.ctms_backend.patientportal.exception.NoLinkedSubjectException;
import com.ctms.ctms_backend.patientportal.service.PatientContextService;
import com.ctms.ctms_backend.security.AuthenticationService;
import com.ctms.ctms_backend.security.dto.TokenResponse;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.PortalAccountResponse;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.UpdateOwnProfileRequest;
import com.ctms.ctms_backend.subject.service.SubjectPortalAccountService;
import com.ctms.ctms_backend.subject.service.SubjectService;
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.service.VisitService;
import com.ctms.ctms_backend.visit.service.VisitTemplateService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

/** Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), mirrors SystemConfigurationIntegrationTest / FinancialManagementIntegrationTest. */
@SpringBootTest
@Transactional
class PatientPortalIntegrationTest {

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SubjectService subjectService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private SubjectPortalAccountService subjectPortalAccountService;
    @Autowired private AuthenticationService authenticationService;
    @Autowired private PatientContextService patientContextService;
    @Autowired private PatientVisitController patientVisitController;
    @Autowired private PatientDocumentController patientDocumentController;
    @Autowired private PatientProfileController patientProfileController;
    @Autowired private PatientAdverseEventController patientAdverseEventController;

    private com.ctms.ctms_backend.user.User createTestUser(String username, String roleCode) {
        com.ctms.ctms_backend.user.User user = new com.ctms.ctms_backend.user.User();
        user.setUsername(username);
        user.setEmail(username + "@ctms.local");
        user.setFullName("Integration Test User");
        user.setPasswordHash(passwordEncoder.encode("Integration!Test2026Pass"));
        com.ctms.ctms_backend.user.Role role = roleRepository.findByCode(roleCode).orElseThrow();
        user.setRoles(new java.util.HashSet<>(List.of(role)));
        return userRepository.save(user);
    }

    @Autowired private com.ctms.ctms_backend.user.UserRepository userRepository;
    @Autowired private com.ctms.ctms_backend.user.RoleRepository roleRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Principal principal(String username) {
        return () -> username;
    }

    /** Patient controllers are @PreAuthorize-guarded, so calling them directly (bypassing the real
     * HTTP filter chain) needs a SecurityContext populated, same as the JWT filter would do on a
     * real request. */
    private void authenticateAsPatient(String username) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        username, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + com.ctms.ctms_backend.user.Role.PATIENT_SUBJECT))));
    }

    @Test
    void fullPatientPortalLifecycle_accountCreationThroughPatientActions() {
        com.ctms.ctms_backend.user.User manager = createTestUser("pp-mgr-it1", com.ctms.ctms_backend.user.Role.STUDY_MANAGER);
        com.ctms.ctms_backend.user.User coordinator = createTestUser("pp-coord-it1", com.ctms.ctms_backend.user.Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Patient Portal IT Trial", "PP-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "PP-IT-SITE-1", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        visitTemplateService.create(
                new CreateVisitTemplateRequest(study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null),
                manager.getUsername());

        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.of(2026, 1, 1), List.of()),
                coordinator.getUsername());

        // 1. Staff creates the portal account -- deterministic username/password, forced change.
        PortalAccountResponse account = subjectPortalAccountService.createPortalAccount(subject.id(), coordinator.getUsername());
        assertEquals(subject.subjectCode().toLowerCase(), account.username());
        assertEquals("Jane@01011990#1", account.temporaryPassword());

        TokenResponse firstLogin = authenticationService.login(account.username(), account.temporaryPassword());
        assertTrue(firstLogin.mustChangePassword());

        authenticationService.changePassword(account.username(), account.temporaryPassword(), "MyOwnPass@2026!");
        TokenResponse afterChange = authenticationService.login(account.username(), "MyOwnPass@2026!");
        assertFalse(afterChange.mustChangePassword());

        // 2. Patient resolves to exactly their own subject.
        assertEquals(subject.id(), patientContextService.resolveCurrentSubject(account.username()).getId());
        authenticateAsPatient(account.username());

        // 3. Visit schedule -- delegates straight to the real VisitService.
        SubjectVisitScheduleResponse schedule = patientVisitController.mySchedule(principal(account.username()));
        assertEquals(1, schedule.visits().size());
        assertEquals("Screening Visit", schedule.visits().get(0).name());

        // 4. Document upload lands PENDING_REVIEW, not CURRENT.
        MockMultipartFile file = new MockMultipartFile("file", "lab.pdf", "application/pdf", "content".getBytes());
        DocumentResponse uploaded = patientDocumentController.upload(
                principal(account.username()), "LAB_RESULTS", "My Lab Report", LocalDate.of(2026, 1, 5), file);
        assertNull(uploaded.currentVersion(), "patient upload must not auto-promote to CURRENT");

        // 5. Profile update -- only whitelisted contact fields.
        SubjectResponse updatedProfile = patientProfileController.update(
                principal(account.username()),
                new UpdateOwnProfileRequest("555-0000", "jane.updated@example.com", "99 New St", "New Contact"));
        assertEquals("555-0000", updatedProfile.contactPhone());
        assertEquals("Jane", updatedProfile.firstName()); // untouched -- staff-only field

        // 6. AE self-report lands OPEN, visible via the existing staff mechanism.
        AdverseEventResponse ae = patientAdverseEventController.report(
                principal(account.username()), new PatientReportAdverseEventRequest("Mild headache", "MILD"));
        assertEquals("OPEN", ae.status());
        assertEquals(subject.id(), ae.subjectId());
    }

    @Test
    void secondPatientNeverSeesFirstPatientsData() {
        com.ctms.ctms_backend.user.User manager = createTestUser("pp-mgr-it2", com.ctms.ctms_backend.user.Role.STUDY_MANAGER);
        com.ctms.ctms_backend.user.User coordinator = createTestUser("pp-coord-it2", com.ctms.ctms_backend.user.Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Patient Portal IT Trial 2", "PP-IT-PROTO-2", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "PP-IT-SITE-2", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        visitTemplateService.create(
                new CreateVisitTemplateRequest(study.id(), "Screening Visit", 1, 0, 1, 1, "Vitals", "ONSITE", null),
                manager.getUsername());

        SubjectResponse subjectA = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Alice", "Alpha", LocalDate.of(1985, 5, 5), "FEMALE", null, null, null, null,
                        null, null, LocalDate.of(2026, 1, 1), List.of()),
                coordinator.getUsername());
        SubjectResponse subjectB = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Bob", "Beta", LocalDate.of(1988, 8, 8), "MALE", null, null, null, null,
                        null, null, LocalDate.of(2026, 1, 1), List.of()),
                coordinator.getUsername());

        PortalAccountResponse accountA = subjectPortalAccountService.createPortalAccount(subjectA.id(), coordinator.getUsername());
        PortalAccountResponse accountB = subjectPortalAccountService.createPortalAccount(subjectB.id(), coordinator.getUsername());

        // Each patient's own-subject resolution never crosses over -- there is no endpoint that
        // accepts a client-supplied subject ID, so this is the entire attack surface.
        assertEquals(subjectA.id(), patientContextService.resolveCurrentSubject(accountA.username()).getId());
        assertEquals(subjectB.id(), patientContextService.resolveCurrentSubject(accountB.username()).getId());
        assertThrows(NoLinkedSubjectException.class, () -> patientContextService.resolveCurrentSubject("someone-else"));

        // Both subjects share the SAME study -- document isolation must be per-patient (owner),
        // not per-study, otherwise A's upload would leak to B despite being different subjects.
        authenticateAsPatient(accountA.username());
        MockMultipartFile fileA = new MockMultipartFile("file", "a.pdf", "application/pdf", "content".getBytes());
        patientDocumentController.upload(principal(accountA.username()), "LAB_RESULTS", "Alice's Report", LocalDate.of(2026, 1, 1), fileA);

        authenticateAsPatient(accountB.username());
        var bDocuments = patientDocumentController.list(
                principal(accountB.username()), org.springframework.data.domain.PageRequest.of(0, 20));
        assertTrue(bDocuments.getContent().isEmpty(), "patient B must not see patient A's uploaded document");
    }

    @Test
    void resetPortalPassword_recomputesFromCurrentDataAndForcesChangeAgain() {
        com.ctms.ctms_backend.user.User manager = createTestUser("pp-mgr-it3", com.ctms.ctms_backend.user.Role.STUDY_MANAGER);
        com.ctms.ctms_backend.user.User coordinator = createTestUser("pp-coord-it3", com.ctms.ctms_backend.user.Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Patient Portal IT Trial 3", "PP-IT-PROTO-3", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "PP-IT-SITE-3", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Carl", "Gamma", LocalDate.of(1975, 3, 3), "MALE", null, null, null, null,
                        null, null, LocalDate.of(2026, 1, 1), List.of()),
                coordinator.getUsername());

        PortalAccountResponse account = subjectPortalAccountService.createPortalAccount(subject.id(), coordinator.getUsername());
        authenticationService.changePassword(account.username(), account.temporaryPassword(), "MyOwnPass@2026!");
        TokenResponse loggedInAfterChange = authenticationService.login(account.username(), "MyOwnPass@2026!");
        assertFalse(loggedInAfterChange.mustChangePassword());

        PortalAccountResponse reset = subjectPortalAccountService.resetPortalPassword(subject.id(), coordinator.getUsername());
        assertEquals("Carl@03031975#1", reset.temporaryPassword());

        TokenResponse loggedInAfterReset = authenticationService.login(account.username(), reset.temporaryPassword());
        assertTrue(loggedInAfterReset.mustChangePassword());
    }
}
