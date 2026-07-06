package com.ctms.ctms_backend.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.audit.AuditLogRepository;
import com.ctms.ctms_backend.notification.NotificationRepository;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest;
import com.ctms.ctms_backend.site.exception.SiteActivationBlockedException;
import com.ctms.ctms_backend.site.service.SiteActivationService;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), not Testcontainers -- mirrors StudyManagementIntegrationTest /
 * DocumentApprovalWorkflowIntegrationTest. Rolled back after each test, except AuditService
 * writes (its own REQUIRES_NEW transaction) which persist as a harmless side effect.
 */
@SpringBootTest
@Transactional
class SiteManagementIntegrationTest {

    @Autowired
    private StudyService studyService;

    @Autowired
    private SiteService siteService;

    @Autowired
    private SiteActivationService siteActivationService;

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
    void fullLifecycle_registerThroughAutoActivation_persistsAuditAndNotifications() {
        User manager = createTestUser("site-mgr-it", Role.STUDY_MANAGER);
        User cra = createTestUser("site-cra-it", Role.CRA_MONITOR);

        StudyResponse study = studyService.createStudy(
                new CreateStudyRequest("Site IT Trial", "SITE-IT-PROTO-1", "1.0", "PHASE_III", "Acme", null, null, null),
                manager.getUsername());

        SiteResponse site = siteService.registerSite(
                new CreateSiteRequest(
                        study.id(), "SITE-IT-001", "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                manager.getUsername());
        assertEquals("PENDING_ACTIVATION", site.status());

        siteService.assignCra(site.id(), new com.ctms.ctms_backend.site.dto.AssignCraRequest(cra.getUsername(), null), manager.getUsername());

        // Mark 4 of 5 items complete.
        siteActivationService.updateChecklistItem(site.id(), "FEASIBILITY_COMPLETION",
                new UpdateChecklistItemRequest("COMPLETE", null, "done"), manager.getUsername());
        siteActivationService.updateChecklistItem(site.id(), "IRB_EC_APPROVAL",
                new UpdateChecklistItemRequest("COMPLETE", null, "done"), manager.getUsername());
        siteActivationService.updateChecklistItem(site.id(), "CONTRACT_COMPLETION",
                new UpdateChecklistItemRequest("COMPLETE", null, "done"), manager.getUsername());
        siteActivationService.updateChecklistItem(site.id(), "ESSENTIAL_DOCUMENTS_SUBMISSION",
                new UpdateChecklistItemRequest("COMPLETE", null, "done"), manager.getUsername());

        SiteActivationBlockedException ex = assertThrows(SiteActivationBlockedException.class,
                () -> siteActivationService.attemptActivation(
                        site.id(), new com.ctms.ctms_backend.site.dto.AttemptActivationRequest("Integration!Test2026Pass", "attempting"),
                        manager.getUsername()));
        assertEquals(List.of("Site Initiation Visit"), ex.getMissingItems());

        // Complete the last item -- should auto-activate.
        siteActivationService.updateChecklistItem(site.id(), "SITE_INITIATION_VISIT",
                new UpdateChecklistItemRequest("COMPLETE", null, "done"), manager.getUsername());

        SiteResponse activated = siteService.get(site.id());
        assertEquals("ACTIVE", activated.status());
        assertTrue(activated.activationDate() != null);

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> "Site".equals(a.getEntityName()) && String.valueOf(site.id()).equals(a.getEntityId()))
                .count();
        assertTrue(auditCount >= 3, "expected CREATE + blocked-attempt STATE_CHANGE + promotion STATE_CHANGE, got " + auditCount);

        long notificationCount = notificationRepository.findAll().stream()
                .filter(n -> "SITE_ACTIVATED".equals(n.getType()))
                .count();
        assertEquals(2, notificationCount, "expected notifications for both the creator and the assigned CRA");
    }
}
