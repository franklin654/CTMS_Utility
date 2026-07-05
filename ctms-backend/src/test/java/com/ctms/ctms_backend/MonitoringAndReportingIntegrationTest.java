package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctms.ctms_backend.dashboard.dto.DashboardSummaryResponse;
import com.ctms.ctms_backend.dashboard.service.DashboardService;
import com.ctms.ctms_backend.milestone.dto.CreateMilestoneRequest;
import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import com.ctms.ctms_backend.milestone.dto.RecordMilestoneActualRequest;
import com.ctms.ctms_backend.milestone.exception.DuplicateMilestoneTypeException;
import com.ctms.ctms_backend.milestone.service.MilestoneService;
import com.ctms.ctms_backend.monitoring.dto.LogMonitoringVisitRequest;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitReportResponse;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitResponse;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import com.ctms.ctms_backend.monitoring.service.MonitoringVisitReportService;
import com.ctms.ctms_backend.monitoring.service.MonitoringVisitService;
import com.ctms.ctms_backend.notification.NotificationRepository;
import com.ctms.ctms_backend.site.dto.AssignCraRequest;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.service.SiteService;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.service.StudyService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars pointed at the
 * dedicated ctms_testdb), mirrors ClinicalSafetyIntegrationTest / TaskManagementIntegrationTest. */
@SpringBootTest
@Transactional
class MonitoringAndReportingIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private SiteService siteService;
    @Autowired private MonitoringVisitService monitoringVisitService;
    @Autowired private MonitoringVisitReportService monitoringVisitReportService;
    @Autowired private MilestoneService milestoneService;
    @Autowired private DashboardService dashboardService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

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

    private void authenticateAs(String... roleCodes) {
        var authorities = List.of(roleCodes).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("actor", "n/a", authorities));
    }

    private StudyResponse createStudy(String suffix, String managerUsername) {
        return studyService.createStudy(
                new CreateStudyRequest("Monitoring IT Trial " + suffix, "MON-IT-PROTO-" + suffix, "1.0", "PHASE_III", "Acme", null, null, null),
                managerUsername);
    }

    private SiteResponse createSite(Long studyId, String suffix, String managerUsername) {
        return siteService.registerSite(
                new CreateSiteRequest(
                        studyId, "MON-IT-SITE-" + suffix, "IT Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                        "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null),
                managerUsername);
    }

    @Test
    void assignCra_withBackup_notifiesBoth() {
        User manager = createTestUser("mon-mgr-it1", Role.STUDY_MANAGER);
        User primary = createTestUser("mon-cra-primary-it1", Role.CRA_MONITOR);
        User backup = createTestUser("mon-cra-backup-it1", Role.CRA_MONITOR);
        StudyResponse study = createStudy("1", manager.getUsername());
        SiteResponse site = createSite(study.id(), "1", manager.getUsername());

        SiteResponse updated = siteService.assignCra(
                site.id(), new AssignCraRequest(primary.getUsername(), backup.getUsername()), manager.getUsername());

        assertEquals(primary.getUsername(), updated.assignedCraUsername());
        assertEquals(backup.getUsername(), updated.backupCraUsername());

        long primaryNotifications = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(primary.getId()) && "CRA_ASSIGNED".equals(n.getType()))
                .count();
        long backupNotifications = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(backup.getId()) && "CRA_ASSIGNED".equals(n.getType()))
                .count();
        assertEquals(1, primaryNotifications);
        assertEquals(1, backupNotifications);
    }

    @Test
    void monitoringVisit_logAndReportRoundTrip() {
        User manager = createTestUser("mon-mgr-it2", Role.STUDY_MANAGER);
        User cra = createTestUser("mon-cra-it2", Role.CRA_MONITOR);
        StudyResponse study = createStudy("2", manager.getUsername());
        SiteResponse site = createSite(study.id(), "2", manager.getUsername());

        MonitoringVisitResponse visit = monitoringVisitService.log(
                new LogMonitoringVisitRequest(site.id(), "SIV", LocalDate.of(2026, 1, 15), "Site ready", null, "Regulatory docs checked"),
                cra.getUsername());
        assertEquals("SIV", visit.visitType());

        MonitoringVisit entity = monitoringVisitService.findMonitoringVisit(visit.id());
        byte[] content = "monitoring report bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "siv-report.pdf", "application/pdf", content);
        MonitoringVisitReportResponse report = monitoringVisitReportService.upload(entity, file, cra.getUsername());

        try (InputStream downloaded = monitoringVisitReportService.download(report.id())) {
            assertArrayEquals(content, downloaded.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void milestone_duplicateTypeRejectedAndDelayedFlagCorrect() {
        User manager = createTestUser("mon-mgr-it3", Role.STUDY_MANAGER);
        StudyResponse study = createStudy("3", manager.getUsername());

        MilestoneResponse fpi = milestoneService.create(
                new CreateMilestoneRequest(study.id(), "FPI", LocalDate.now().minusDays(5)), manager.getUsername());
        assertEquals(true, fpi.delayed());

        assertThrows(DuplicateMilestoneTypeException.class, () -> milestoneService.create(
                new CreateMilestoneRequest(study.id(), "FPI", LocalDate.now()), manager.getUsername()));

        MilestoneResponse recorded = milestoneService.recordActual(
                fpi.id(), new RecordMilestoneActualRequest(LocalDate.now()), manager.getUsername());
        assertEquals(true, recorded.delayed());
    }

    @Test
    void dashboard_craScopedVsPortfolioWide() {
        User manager = createTestUser("mon-mgr-it4", Role.STUDY_MANAGER);
        User craA = createTestUser("mon-cra-a-it4", Role.CRA_MONITOR);
        User craB = createTestUser("mon-cra-b-it4", Role.CRA_MONITOR);
        StudyResponse study = createStudy("4", manager.getUsername());
        SiteResponse siteA = createSite(study.id(), "4a", manager.getUsername());
        SiteResponse siteB = createSite(study.id(), "4b", manager.getUsername());
        siteService.assignCra(siteA.id(), new AssignCraRequest(craA.getUsername(), null), manager.getUsername());
        siteService.assignCra(siteB.id(), new AssignCraRequest(craB.getUsername(), null), manager.getUsername());

        authenticateAs(Role.CRA_MONITOR);
        DashboardSummaryResponse craView = dashboardService.summary(study.id(), null, null, null, craA.getUsername());
        long craSiteCount = craView.sitesByCountry().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(1, craSiteCount);

        authenticateAs(Role.STUDY_MANAGER);
        DashboardSummaryResponse managerView = dashboardService.summary(study.id(), null, null, null, manager.getUsername());
        long managerSiteCount = managerView.sitesByCountry().values().stream().mapToLong(Long::longValue).sum();
        assertTrue(managerSiteCount >= 2);
    }
}
