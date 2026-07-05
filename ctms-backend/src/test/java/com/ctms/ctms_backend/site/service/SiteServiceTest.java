package com.ctms.ctms_backend.site.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.site.dto.AssignCraRequest;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import com.ctms.ctms_backend.site.exception.DuplicateSiteCodeException;
import com.ctms.ctms_backend.site.exception.InvalidCraAssignmentException;
import com.ctms.ctms_backend.site.repository.SiteActivationChecklistItemRepository;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;
    @Mock
    private SiteActivationChecklistItemRepository checklistRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SiteService siteService;

    private User creator;
    private Study study;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setUsername("study.manager");
        lenient().when(userRepository.findByUsername("study.manager")).thenReturn(Optional.of(creator));

        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        lenient().when(studyRepository.findById(10L)).thenReturn(Optional.of(study));

        lenient().when(siteRepository.save(any(Site.class))).thenAnswer(invocation -> {
            Site s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(100L);
            }
            return s;
        });
        lenient().when(checklistRepository.save(any(SiteActivationChecklistItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateSiteRequest validRequest() {
        return new CreateSiteRequest(
                10L, "SITE-001", "Test Hospital", "1 Main St", null, "Boston", null, null, "USA",
                "Dr. Smith", "drsmith@example.com", "Jane", "jane@example.com", "555-1234", "Completed", null);
    }

    @Test
    void registerSite_duplicateSiteCode_throws() {
        when(siteRepository.existsBySiteCode("SITE-001")).thenReturn(true);
        assertThrows(DuplicateSiteCodeException.class,
                () -> siteService.registerSite(validRequest(), "study.manager"));
    }

    @Test
    void registerSite_happyPath_defaultsToPendingActivation() {
        when(siteRepository.existsBySiteCode("SITE-001")).thenReturn(false);
        SiteResponse response = siteService.registerSite(validRequest(), "study.manager");
        assertEquals("PENDING_ACTIVATION", response.status());
        assertEquals("SITE-001", response.siteCode());
    }

    @Test
    void registerSite_seedsAllFiveChecklistItems() {
        when(siteRepository.existsBySiteCode("SITE-001")).thenReturn(false);
        siteService.registerSite(validRequest(), "study.manager");

        ArgumentCaptor<SiteActivationChecklistItem> captor = ArgumentCaptor.forClass(SiteActivationChecklistItem.class);
        verify(checklistRepository, times(5)).save(captor.capture());
        assertEquals(5, captor.getAllValues().size());
    }

    @Test
    void assignCra_nonCraUser_throwsInvalidCraAssignmentException() {
        Site site = new Site();
        site.setId(100L);
        site.setStudy(study);
        site.setCreatedBy(creator);
        site.setModifiedBy(creator);
        when(siteRepository.findById(100L)).thenReturn(Optional.of(site));

        User notCra = new User();
        notCra.setId(2L);
        notCra.setUsername("not.cra");
        when(userRepository.findByUsername("not.cra")).thenReturn(Optional.of(notCra));

        assertThrows(InvalidCraAssignmentException.class,
                () -> siteService.assignCra(100L, new AssignCraRequest("not.cra", null), "study.manager"));
    }

    @Test
    void assignCra_validCra_succeeds() {
        Site site = new Site();
        site.setId(100L);
        site.setStudy(study);
        site.setCreatedBy(creator);
        site.setModifiedBy(creator);
        when(siteRepository.findById(100L)).thenReturn(Optional.of(site));

        User cra = craUser(3L, "cra.monitor");
        when(userRepository.findByUsername("cra.monitor")).thenReturn(Optional.of(cra));

        SiteResponse response = siteService.assignCra(100L, new AssignCraRequest("cra.monitor", null), "study.manager");
        assertEquals("cra.monitor", response.assignedCraUsername());
        verify(notificationService).notify(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("CRA_ASSIGNED"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assignCra_withBackupCra_setsBothAndNotifiesBoth() {
        Site site = new Site();
        site.setId(100L);
        site.setStudy(study);
        site.setCreatedBy(creator);
        site.setModifiedBy(creator);
        when(siteRepository.findById(100L)).thenReturn(Optional.of(site));

        User primary = craUser(3L, "cra.primary");
        User backup = craUser(4L, "cra.backup");
        when(userRepository.findByUsername("cra.primary")).thenReturn(Optional.of(primary));
        when(userRepository.findByUsername("cra.backup")).thenReturn(Optional.of(backup));

        SiteResponse response = siteService.assignCra(100L, new AssignCraRequest("cra.primary", "cra.backup"), "study.manager");
        assertEquals("cra.primary", response.assignedCraUsername());
        assertEquals("cra.backup", response.backupCraUsername());
        verify(notificationService).notify(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(notificationService).notify(org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private User craUser(Long id, String username) {
        User cra = new User();
        cra.setId(id);
        cra.setUsername(username);
        com.ctms.ctms_backend.user.Role craRole = new com.ctms.ctms_backend.user.Role();
        craRole.setCode(Role.CRA_MONITOR);
        cra.setRoles(java.util.Set.of(craRole));
        return cra;
    }
}
