package com.ctms.ctms_backend.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.adverseevent.entity.AdverseEventSeverity;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventStatus;
import com.ctms.ctms_backend.adverseevent.repository.AdverseEventRepository;
import com.ctms.ctms_backend.dashboard.dto.DashboardSummaryResponse;
import com.ctms.ctms_backend.milestone.repository.MilestoneRepository;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteStatus;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private SiteRepository siteRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private AdverseEventRepository adverseEventRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudyRepository studyRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Study study;
    private Site siteA;
    private Site siteB;

    @BeforeEach
    void setUp() {
        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        study.setPhase("PHASE_III");

        siteA = new Site();
        siteA.setId(100L);
        siteA.setSiteCode("SITE-A");
        siteA.setName("Site A");
        siteA.setCountry("USA");
        siteA.setStudy(study);
        siteA.setStatus(SiteStatus.ACTIVE);

        siteB = new Site();
        siteB.setId(200L);
        siteB.setSiteCode("SITE-B");
        siteB.setName("Site B");
        siteB.setCountry("India");
        siteB.setStudy(study);
        siteB.setStatus(SiteStatus.PENDING_ACTIVATION);

        lenient().when(siteRepository.findForDashboard(any(), any(), any(), any())).thenReturn(List.of(siteA, siteB));
        lenient().when(milestoneRepository.findByStudyId(10L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        var auth = new UsernamePasswordAuthenticationToken("actor", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void summary_portfolioWideForStudyManager() {
        authenticateAs(Role.STUDY_MANAGER);
        when(subjectRepository.countBySiteIdInAndStatus(any(), any())).thenReturn(0L);
        when(visitRepository.countByStatusGroupedBySite(any())).thenReturn(List.of());
        when(adverseEventRepository.countHighSeverityOpenGroupedBySite(any(), any(), any())).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.summary(null, null, null, null, "study.manager");

        assertEquals(2L, response.sitesByCountry().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(1L, response.siteActivationByStatus().get("ACTIVE"));
        assertEquals(1L, response.siteActivationByStatus().get("PENDING_ACTIVATION"));
    }

    @Test
    void summary_craScopedToOwnSitesOnly() {
        authenticateAs(Role.CRA_MONITOR);
        User cra = new User();
        cra.setId(5L);
        cra.setUsername("cra.monitor");
        when(userRepository.findByUsername("cra.monitor")).thenReturn(Optional.of(cra));
        when(siteRepository.findByAssignedOrBackupCra(5L)).thenReturn(List.of(siteA));
        when(subjectRepository.countBySiteIdInAndStatus(any(), any())).thenReturn(0L);
        when(visitRepository.countByStatusGroupedBySite(any())).thenReturn(List.of());
        when(adverseEventRepository.countHighSeverityOpenGroupedBySite(any(), any(), any())).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.summary(null, null, null, null, "cra.monitor");

        assertEquals(1L, response.sitesByCountry().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void summary_highRiskSite_flaggedByMissedVisitRate() {
        authenticateAs(Role.STUDY_MANAGER);
        when(subjectRepository.countBySiteIdInAndStatus(any(), any())).thenReturn(0L);
        // Site A: 3 missed, 7 completed => 30% missed rate > 20% threshold
        when(visitRepository.countByStatusGroupedBySite(any())).thenReturn(List.<Object[]>of(
                new Object[] {100L, VisitStatus.MISSED, 3L},
                new Object[] {100L, VisitStatus.COMPLETED, 7L}));
        when(adverseEventRepository.countHighSeverityOpenGroupedBySite(any(), any(), any())).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.summary(null, null, null, null, "study.manager");

        assertEquals(1, response.highRiskSites().size());
        assertEquals("SITE-A", response.highRiskSites().get(0).siteCode());
    }

    @Test
    void summary_highRiskSite_flaggedByOpenSevereAeCount() {
        authenticateAs(Role.STUDY_MANAGER);
        when(subjectRepository.countBySiteIdInAndStatus(any(), any())).thenReturn(0L);
        when(visitRepository.countByStatusGroupedBySite(any())).thenReturn(List.of());
        when(adverseEventRepository.countHighSeverityOpenGroupedBySite(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {200L, 2L}));

        DashboardSummaryResponse response = dashboardService.summary(null, null, null, null, "study.manager");

        assertEquals(1, response.highRiskSites().size());
        assertEquals("SITE-B", response.highRiskSites().get(0).siteCode());
    }

    @Test
    void summary_noRiskTriggers_noHighRiskSites() {
        authenticateAs(Role.STUDY_MANAGER);
        when(subjectRepository.countBySiteIdInAndStatus(any(), any())).thenReturn(0L);
        when(visitRepository.countByStatusGroupedBySite(any())).thenReturn(List.<Object[]>of(
                new Object[] {100L, VisitStatus.MISSED, 1L},
                new Object[] {100L, VisitStatus.COMPLETED, 9L}));
        when(adverseEventRepository.countHighSeverityOpenGroupedBySite(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {200L, 1L}));

        DashboardSummaryResponse response = dashboardService.summary(null, null, null, null, "study.manager");

        assertTrue(response.highRiskSites().isEmpty());
    }

    @Test
    void filterOptions_returnsDistinctCountriesAndPhases() {
        when(siteRepository.findDistinctCountries()).thenReturn(List.of("India", "USA"));
        when(studyRepository.findDistinctPhases()).thenReturn(List.of("PHASE_II", "PHASE_III"));

        var options = dashboardService.filterOptions();

        assertEquals(List.of("India", "USA"), options.countries());
        assertEquals(List.of("PHASE_II", "PHASE_III"), options.phases());
    }
}
