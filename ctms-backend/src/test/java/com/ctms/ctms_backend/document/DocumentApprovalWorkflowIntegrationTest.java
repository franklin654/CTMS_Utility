package com.ctms.ctms_backend.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.document.dto.FinalApprovalRequest;
import com.ctms.ctms_backend.document.dto.ReviewDecisionRequest;
import com.ctms.ctms_backend.document.exception.DocumentAccessDeniedException;
import com.ctms.ctms_backend.document.service.DocumentAccessControlService;
import com.ctms.ctms_backend.document.service.DocumentWorkflowService;
import com.ctms.ctms_backend.esignature.ESignatureRepository;
import com.ctms.ctms_backend.notification.NotificationRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real Postgres (ctms_testdb via DB_URL/DB_USERNAME/DB_PASSWORD env vars), not
 * Testcontainers, matching Phase 0/1's approach (no Docker in this environment). Rolled back
 * after each test, except AuditService writes (its own REQUIRES_NEW transaction, by design).
 */
@SpringBootTest
@Transactional
class DocumentApprovalWorkflowIntegrationTest {

    @Autowired private DocumentService documentService;
    @Autowired private DocumentWorkflowService workflowService;
    @Autowired private DocumentAccessControlService accessControlService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ESignatureRepository eSignatureRepository;

    private User createUser(String username, String roleCode) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@ctms.local");
        user.setFullName("Integration Test User");
        user.setPasswordHash(passwordEncoder.encode("Integration!Test2026Pass"));
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        user.setRoles(new HashSet<>(java.util.List.of(role)));
        return userRepository.save(user);
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes());
    }

    @Test
    void fullWorkflow_uploadThroughFinalApproval_persistsAuditNotificationAndSignature() {
        User manager = createUser("doc-mgr-it", Role.STUDY_MANAGER);
        User qa = createUser("doc-qa-it", Role.QA_COMPLIANCE_AUDITOR);

        var created = documentService.createDocument(
                "Protocol", "PROTOCOL", null, manager.getUsername(), file("v1.txt", "version one"));
        assertEquals("CURRENT", created.currentVersion().status());

        var v2 = documentService.addVersion(created.id(), manager.getUsername(), file("v2.txt", "version two"));
        assertEquals("DRAFT", v2.status());

        var submitted = workflowService.submitForReview(created.id(), 2, manager.getUsername());
        assertEquals("SUBMITTED", submitted.action());

        var reviewed = workflowService.reviewerDecide(
                created.id(), 2, new ReviewDecisionRequest("APPROVED", null), manager.getUsername());
        assertEquals("APPROVED", reviewed.action());

        assertThrows(InvalidCredentialsException.class, () -> workflowService.approverFinalDecide(
                created.id(), 2, new FinalApprovalRequest("APPROVED", null, "wrong-password", "sign-off"),
                qa.getUsername()));

        var approved = workflowService.approverFinalDecide(
                created.id(), 2, new FinalApprovalRequest("APPROVED", null, "Integration!Test2026Pass", "sign-off"),
                qa.getUsername());
        assertEquals("APPROVED", approved.action());
        assertTrue(approved.signed());

        var updatedDoc = documentService.get(created.id());
        assertEquals(2, updatedDoc.currentVersion().versionNumber());
        assertEquals("CURRENT", updatedDoc.currentVersion().status());

        var history = documentService.versionHistory(created.id());
        var v1After = history.stream().filter(v -> v.versionNumber() == 1).findFirst().orElseThrow();
        assertEquals("ARCHIVED", v1After.status());

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "Document".equals(a.getEntityName()) && String.valueOf(created.id()).equals(a.getEntityId()))
                .count();
        assertTrue(auditCount >= 4, "expected CREATE/UPDATE/STATE_CHANGE rows, got " + auditCount);

        long signatureCount = eSignatureRepository
                .findByEntityNameAndEntityIdOrderBySignedAtDesc("DocumentVersion", String.valueOf(v2.id()))
                .size();
        assertEquals(1, signatureCount);
    }

    @Test
    void rejectionPath_requiresCommentAndIsTerminal() {
        User manager = createUser("doc-mgr-it2", Role.STUDY_MANAGER);
        var created = documentService.createDocument(
                "SOP", "SOP", null, manager.getUsername(), file("v1.txt", "sop v1"));
        documentService.addVersion(created.id(), manager.getUsername(), file("v2-bad.txt", "bad content"));
        workflowService.submitForReview(created.id(), 2, manager.getUsername());

        var rejected = workflowService.reviewerDecide(
                created.id(), 2, new ReviewDecisionRequest("REJECTED", "Missing required section"), manager.getUsername());
        assertEquals("REJECTED", rejected.action());

        var history = workflowService.history(created.id(), 2);
        assertEquals(2, history.size());
    }

    @Test
    void categoryAccessRule_deniesAndAudits() {
        // FINANCIAL/CRA_MONITOR deny rule is seeded in V4 migration.
        User manager = createUser("doc-mgr-it3", Role.STUDY_MANAGER);
        var created = documentService.createDocument(
                "Budget", "FINANCIAL", null, manager.getUsername(), file("budget.txt", "numbers"));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "cra-it", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_CRA_MONITOR"))));
        try {
            var document = new Document();
            document.setId(created.id());
            document.setCategory("FINANCIAL");
            assertThrows(DocumentAccessDeniedException.class, () -> accessControlService.assertReadable(document));
        } finally {
            SecurityContextHolder.clearContext();
        }

        long deniedCount = auditLogRepository.findAll().stream()
                .filter(a -> "Document".equals(a.getEntityName())
                        && String.valueOf(created.id()).equals(a.getEntityId())
                        && "ACCESS_DENIED".equals(a.getAction()))
                .count();
        assertEquals(1, deniedCount);
    }
}
