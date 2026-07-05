package com.ctms.ctms_backend.study;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.esignature.ESignatureRepository;
import com.ctms.ctms_backend.notification.NotificationRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.dto.CloseoutStudyRequest;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.dto.TransitionStudyRequest;
import com.ctms.ctms_backend.study.exception.StudyClosedException;
import com.ctms.ctms_backend.study.exception.StudyFieldLockedException;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars), not Testcontainers,
 * since Docker isn't available in this environment (mirrors NotificationServiceIntegrationTest).
 * Rolled back after each test -- except AuditService writes, which deliberately run in their own
 * REQUIRES_NEW transaction so they survive rollback (by design, see AuditService), so a handful of
 * audit_log rows from this test will persist; that's an accepted, harmless side effect.
 */
@SpringBootTest
@Transactional
class StudyManagementIntegrationTest {

    @Autowired
    private StudyService studyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ESignatureRepository eSignatureRepository;

    private User createTestUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@ctms.local");
        user.setFullName("Integration Test User");
        user.setPasswordHash(passwordEncoder.encode("Integration!Test2026Pass"));
        Role role = roleRepository.findByCode(Role.STUDY_MANAGER).orElseThrow();
        user.setRoles(new HashSet<>(java.util.List.of(role)));
        return userRepository.save(user);
    }

    @Test
    void fullLifecycle_createThroughCloseout_persistsAuditNotificationAndSignature() {
        User manager = createTestUser("study-mgr-it");

        StudyResponse created = studyService.createStudy(
                new CreateStudyRequest(
                        "IT Trial", "IT-PROTO-1", "1.0", "PHASE_III", "Acme",
                        null, null, "integration test study"),
                manager.getUsername());
        assertEquals("DRAFT", created.status());
        assertTrue(created.studyCode().startsWith("ST-"));

        StudyResponse afterActive = studyService.transition(
                created.id(), new TransitionStudyRequest("ACTIVE", "IRB approved"), manager.getUsername());
        assertEquals("ACTIVE", afterActive.status());

        StudyResponse afterConduct = studyService.transition(
                created.id(), new TransitionStudyRequest("CONDUCT", "Enrollment complete"), manager.getUsername());
        assertEquals("CONDUCT", afterConduct.status());

        // Wrong password must not close the study out.
        assertThrows(InvalidCredentialsException.class, () -> studyService.closeout(
                created.id(), new CloseoutStudyRequest("wrong-password", "Trial complete"), manager.getUsername()));

        StudyResponse closedOut = studyService.closeout(
                created.id(), new CloseoutStudyRequest("Integration!Test2026Pass", "Trial complete"), manager.getUsername());
        assertEquals("CLOSEOUT", closedOut.status());

        // Fully locked once closed out.
        assertThrows(StudyClosedException.class, () -> studyService.updateStudy(
                created.id(),
                new com.ctms.ctms_backend.study.dto.UpdateStudyRequest(
                        "x", "IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null, null, null),
                manager.getUsername()));

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "Study".equals(a.getEntityName()) && String.valueOf(created.id()).equals(a.getEntityId()))
                .count();
        assertTrue(auditCount >= 5, "expected CREATE + 3x STATE_CHANGE (incl. e-signature's own entry) audit rows, got " + auditCount);

        long notificationCount = notificationRepository.findAll().stream()
                .filter(n -> "STUDY_STATE_CHANGE".equals(n.getType()))
                .count();
        assertTrue(notificationCount >= 3, "expected 3 lifecycle notifications, got " + notificationCount);

        long signatureCount = eSignatureRepository.findByEntityNameAndEntityIdOrderBySignedAtDesc(
                        "Study", String.valueOf(created.id()))
                .size();
        assertEquals(1, signatureCount, "expected exactly one e-signature for the closeout transition");
    }

    @Test
    void updateStudy_protocolIdChangeAfterDraft_throwsFieldLocked() {
        User manager = createTestUser("study-mgr-it2");
        StudyResponse created = studyService.createStudy(
                new CreateStudyRequest("Lock Test", "LOCK-PROTO-1", "1.0", "PHASE_II", "Acme", null, null, null),
                manager.getUsername());

        studyService.transition(created.id(), new TransitionStudyRequest("ACTIVE", "approved"), manager.getUsername());

        assertThrows(StudyFieldLockedException.class, () -> studyService.updateStudy(
                created.id(),
                new com.ctms.ctms_backend.study.dto.UpdateStudyRequest(
                        "x", "CHANGED-PROTO", "1.0", "PHASE_II", "Acme", null, null, null, null, null),
                manager.getUsername()));
    }
}
