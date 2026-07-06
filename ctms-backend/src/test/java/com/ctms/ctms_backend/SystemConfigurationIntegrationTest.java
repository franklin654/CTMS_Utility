package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.dto.CreateDocumentRequirementRequest;
import com.ctms.ctms_backend.document.exception.MissingMandatoryDocumentsException;
import com.ctms.ctms_backend.document.service.DocumentRequirementService;
import com.ctms.ctms_backend.rules.RuleCompilationException;
import com.ctms.ctms_backend.rules.RuleDefinitionDetailResponse;
import com.ctms.ctms_backend.rules.RuleSetDetailResponse;
import com.ctms.ctms_backend.rules.RuleSetService;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.dto.TransitionStudyRequest;
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
import com.ctms.ctms_backend.visit.dto.VisitTemplateResponse;
import com.ctms.ctms_backend.visit.exception.CrossStudyDependencyException;
import com.ctms.ctms_backend.visit.exception.VisitDependencyNotMetException;
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
 * dedicated ctms_testdb), mirrors FinancialManagementIntegrationTest / ClinicalSafetyIntegrationTest. */
@SpringBootTest
@Transactional
class SystemConfigurationIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private SubjectService subjectService;
    @Autowired private VisitTemplateService visitTemplateService;
    @Autowired private VisitService visitService;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentRequirementService documentRequirementService;
    @Autowired private RuleSetService ruleSetService;
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
    void ruleSet_addVersion_validDrlActivates_invalidDrlRejectedWithoutChangingActive() {
        User admin = createTestUser("sysconfig-admin-it0", Role.ADMIN);

        RuleSetDetailResponse before = ruleSetService.getDetail("TASK_RULES_DEFAULT");
        int versionsBefore = before.definitions().size();
        RuleDefinitionDetailResponse activeBefore =
                before.definitions().stream().filter(RuleDefinitionDetailResponse::active).findFirst().orElseThrow();

        assertThrows(RuleCompilationException.class, () -> ruleSetService.addDefinition(
                "TASK_RULES_DEFAULT", "this is not valid drl {{{", admin.getUsername()));

        RuleSetDetailResponse afterBadAttempt = ruleSetService.getDetail("TASK_RULES_DEFAULT");
        assertEquals(versionsBefore, afterBadAttempt.definitions().size());
        assertEquals(activeBefore.id(), afterBadAttempt.definitions().stream()
                .filter(RuleDefinitionDetailResponse::active).findFirst().orElseThrow().id());

        String validDrl = "package rules; rule \"noop\" when then end";
        ruleSetService.addDefinition("TASK_RULES_DEFAULT", validDrl, admin.getUsername());

        RuleSetDetailResponse afterGoodAttempt = ruleSetService.getDetail("TASK_RULES_DEFAULT");
        assertEquals(versionsBefore + 1, afterGoodAttempt.definitions().size());
        RuleDefinitionDetailResponse newActive =
                afterGoodAttempt.definitions().stream().filter(RuleDefinitionDetailResponse::active).findFirst().orElseThrow();
        assertEquals(validDrl, newActive.drlContent());
    }

    @Test
    void visitDependency_blocksOutOfOrderCompletion_allowsInOrder() {
        User manager = createTestUser("sysconfig-mgr-it1", Role.STUDY_MANAGER);
        User coordinator = createTestUser("sysconfig-coord-it1", Role.SITE_COORDINATOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("SysConfig IT Trial 1", "SC-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "SC-IT-SITE-1", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());

        VisitTemplateResponse visit1Template = visitTemplateService.create(
                new CreateVisitTemplateRequest(study.id(), "Visit 1", 1, 0, 1, 1, "Vitals", "ONSITE", null),
                manager.getUsername());
        VisitTemplateResponse visit2Template = visitTemplateService.create(
                new CreateVisitTemplateRequest(
                        study.id(), "Visit 2", 2, 14, 1, 1, "Bloodwork", "ONSITE", visit1Template.id()),
                manager.getUsername());
        assertEquals(visit1Template.id(), visit2Template.dependsOnVisitTemplateId());

        SubjectResponse subject = subjectService.enrollSubject(
                new EnrollSubjectRequest(
                        study.id(), site.id(), "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null,
                        null, null, LocalDate.now(), List.of()),
                coordinator.getUsername());

        SubjectVisitScheduleResponse schedule = visitService.schedule(subject.id());
        VisitResponse visit1 = schedule.visits().stream().filter(v -> v.visitTemplateId().equals(visit1Template.id())).findFirst().orElseThrow();
        VisitResponse visit2 = schedule.visits().stream().filter(v -> v.visitTemplateId().equals(visit2Template.id())).findFirst().orElseThrow();

        assertThrows(VisitDependencyNotMetException.class, () -> visitService.markCompleted(
                visit2.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "out of order"), coordinator.getUsername()));

        // Epic 11 Story 01 consent gate -- markCompleted also requires a CURRENT INFORMED_CONSENT
        // document on file for this subject, independent of the dependency guard under test here.
        documentService.createDocument(
                "Consent Form", "INFORMED_CONSENT", study.id(), subject.id(), coordinator.getUsername(),
                new MockMultipartFile("file", "consent.pdf", "application/pdf", "content".getBytes()));

        visitService.markCompleted(visit1.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername());
        VisitResponse visit2Completed = visitService.markCompleted(
                visit2.id(), new MarkVisitCompletedRequest(LocalDate.now(), null, "done"), coordinator.getUsername());
        assertEquals("COMPLETED", visit2Completed.status());
    }

    @Test
    void visitTemplate_dependencyAcrossDifferentStudies_rejected() {
        User manager = createTestUser("sysconfig-mgr-it2", Role.STUDY_MANAGER);
        StudyResponse studyA = studyService.createStudy(
                new CreateStudyRequest("SysConfig IT Trial 2A", "SC-IT-PROTO-2A", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());
        StudyResponse studyB = studyService.createStudy(
                new CreateStudyRequest("SysConfig IT Trial 2B", "SC-IT-PROTO-2B", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());

        VisitTemplateResponse templateA = visitTemplateService.create(
                new CreateVisitTemplateRequest(studyA.id(), "Visit A", 1, 0, 1, 1, "Vitals", "ONSITE", null),
                manager.getUsername());

        assertThrows(CrossStudyDependencyException.class, () -> visitTemplateService.create(
                new CreateVisitTemplateRequest(studyB.id(), "Visit B", 1, 0, 1, 1, "Vitals", "ONSITE", templateA.id()),
                manager.getUsername()));
    }

    @Test
    void documentRequirement_blocksStudyTransitionUntilMandatoryDocumentUploaded() {
        User manager = createTestUser("sysconfig-mgr-it3", Role.STUDY_MANAGER);
        User admin = createTestUser("sysconfig-admin-it3", Role.ADMIN);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("SysConfig IT Trial 3", "SC-IT-PROTO-3", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());

        documentRequirementService.create(
                new CreateDocumentRequirementRequest(study.id(), "ACTIVE", "REGULATORY_APPROVAL", true), admin.getUsername());

        MissingMandatoryDocumentsException ex = assertThrows(MissingMandatoryDocumentsException.class, () -> studyService.transition(
                study.id(), new TransitionStudyRequest("ACTIVE", "IRB approval received"), manager.getUsername()));
        assertTrue(ex.getMissingCategories().contains("REGULATORY_APPROVAL"));

        StudyResponse stillDraft = studyService.get(study.id());
        assertEquals("DRAFT", stillDraft.status());

        documentService.createDocument(
                "IRB Approval Letter", "REGULATORY_APPROVAL", study.id(), null, manager.getUsername(),
                new MockMultipartFile("file", "irb-approval.pdf", "application/pdf", "content".getBytes()));

        StudyResponse activated = studyService.transition(
                study.id(), new TransitionStudyRequest("ACTIVE", "IRB approval received"), manager.getUsername());
        assertEquals("ACTIVE", activated.status());
    }
}
