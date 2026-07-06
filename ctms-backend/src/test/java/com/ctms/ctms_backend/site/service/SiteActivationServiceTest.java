package com.ctms.ctms_backend.site.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.payment.service.PaymentService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.dto.ActivationAttemptResponse;
import com.ctms.ctms_backend.site.dto.AttemptActivationRequest;
import com.ctms.ctms_backend.site.dto.ChecklistItemResponse;
import com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest;
import com.ctms.ctms_backend.site.entity.ChecklistItemStatus;
import com.ctms.ctms_backend.site.entity.ChecklistItemType;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import com.ctms.ctms_backend.site.entity.SiteStatus;
import com.ctms.ctms_backend.site.exception.ChecklistItemNotFoundException;
import com.ctms.ctms_backend.site.exception.SiteActivationBlockedException;
import com.ctms.ctms_backend.site.repository.SiteActivationChecklistItemRepository;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SiteActivationServiceTest {

    @Mock
    private SiteRepository siteRepository;
    @Mock
    private SiteActivationChecklistItemRepository checklistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TaskService taskService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ESignatureService eSignatureService;

    @InjectMocks
    private SiteActivationService siteActivationService;

    private User actor;
    private Site site;
    private List<SiteActivationChecklistItem> items;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(1L);
        actor.setUsername("study.manager");
        lenient().when(userRepository.findByUsername("study.manager")).thenReturn(Optional.of(actor));

        Study study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        study.setCreatedBy(actor);

        lenient().when(userRepository.findByRoles_Code(Role.ADMIN)).thenReturn(List.of());

        site = new Site();
        site.setId(100L);
        site.setSiteCode("SITE-001");
        site.setName("Test Hospital");
        site.setStudy(study);
        site.setStatus(SiteStatus.PENDING_ACTIVATION);
        site.setCreatedBy(actor);
        site.setModifiedBy(actor);
        lenient().when(siteRepository.findById(100L)).thenReturn(Optional.of(site));
        lenient().when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        items = new ArrayList<>();
        for (ChecklistItemType type : ChecklistItemType.values()) {
            SiteActivationChecklistItem item = new SiteActivationChecklistItem();
            item.setSite(site);
            item.setItemType(type);
            item.setStatus(ChecklistItemStatus.PENDING);
            items.add(item);
        }
        lenient().when(checklistRepository.findBySiteIdOrderByItemType(100L)).thenReturn(items);
        lenient().when(checklistRepository.save(any(SiteActivationChecklistItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient().when(eSignatureService.sign(
                        org.mockito.ArgumentMatchers.eq("study.manager"), org.mockito.ArgumentMatchers.eq("correct-password"),
                        org.mockito.ArgumentMatchers.eq("Site"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ESignature(actor, "Site", "100", "activation"));
    }

    private SiteActivationChecklistItem itemOfType(ChecklistItemType type) {
        return items.stream().filter(i -> i.getItemType() == type).findFirst().orElseThrow();
    }

    @Test
    void updateChecklistItem_fourOfFiveComplete_doesNotPromote() {
        for (ChecklistItemType type : ChecklistItemType.values()) {
            if (type != ChecklistItemType.SITE_INITIATION_VISIT) {
                itemOfType(type).setStatus(ChecklistItemStatus.COMPLETE);
            }
        }
        SiteActivationChecklistItem pending = itemOfType(ChecklistItemType.IRB_EC_APPROVAL);
        pending.setStatus(ChecklistItemStatus.PENDING);
        when(checklistRepository.findBySiteIdAndItemType(100L, ChecklistItemType.IRB_EC_APPROVAL))
                .thenReturn(Optional.of(pending));

        siteActivationService.updateChecklistItem(
                100L, "IRB_EC_APPROVAL", new UpdateChecklistItemRequest("COMPLETE", null, "done"), "study.manager");

        assertEquals(SiteStatus.PENDING_ACTIVATION, site.getStatus());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void updateChecklistItem_allFiveComplete_autoPromotesAndNotifies() {
        for (ChecklistItemType type : ChecklistItemType.values()) {
            itemOfType(type).setStatus(ChecklistItemStatus.COMPLETE);
        }
        SiteActivationChecklistItem last = itemOfType(ChecklistItemType.SITE_INITIATION_VISIT);
        when(checklistRepository.findBySiteIdAndItemType(100L, ChecklistItemType.SITE_INITIATION_VISIT))
                .thenReturn(Optional.of(last));

        ChecklistItemResponse response = siteActivationService.updateChecklistItem(
                100L, "SITE_INITIATION_VISIT", new UpdateChecklistItemRequest("COMPLETE", null, "done"), "study.manager");

        assertEquals("COMPLETE", response.status());
        assertEquals(SiteStatus.ACTIVE, site.getStatus());
        assertTrue(site.getActivationDate() != null);
        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any()); // no CRA assigned
    }

    @Test
    void updateChecklistItem_allFiveComplete_withCra_notifiesBoth() {
        User cra = new User();
        cra.setId(5L);
        cra.setUsername("cra.monitor");
        site.setAssignedCra(cra);

        for (ChecklistItemType type : ChecklistItemType.values()) {
            itemOfType(type).setStatus(ChecklistItemStatus.COMPLETE);
        }
        SiteActivationChecklistItem last = itemOfType(ChecklistItemType.SITE_INITIATION_VISIT);
        when(checklistRepository.findBySiteIdAndItemType(100L, ChecklistItemType.SITE_INITIATION_VISIT))
                .thenReturn(Optional.of(last));

        siteActivationService.updateChecklistItem(
                100L, "SITE_INITIATION_VISIT", new UpdateChecklistItemRequest("COMPLETE", null, "done"), "study.manager");

        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void updateChecklistItem_unknownItemType_throws() {
        assertThrows(ChecklistItemNotFoundException.class, () -> siteActivationService.updateChecklistItem(
                100L, "NOT_A_TYPE", new UpdateChecklistItemRequest("COMPLETE", null, null), "study.manager"));
    }

    @Test
    void attemptActivation_missingItems_throwsWithExactList() {
        itemOfType(ChecklistItemType.FEASIBILITY_COMPLETION).setStatus(ChecklistItemStatus.COMPLETE);
        itemOfType(ChecklistItemType.IRB_EC_APPROVAL).setStatus(ChecklistItemStatus.COMPLETE);
        itemOfType(ChecklistItemType.CONTRACT_COMPLETION).setStatus(ChecklistItemStatus.COMPLETE);
        itemOfType(ChecklistItemType.ESSENTIAL_DOCUMENTS_SUBMISSION).setStatus(ChecklistItemStatus.COMPLETE);
        // SITE_INITIATION_VISIT left PENDING

        SiteActivationBlockedException ex = assertThrows(SiteActivationBlockedException.class,
                () -> siteActivationService.attemptActivation(
                        100L, new AttemptActivationRequest("correct-password", "attempting"), "study.manager"));
        assertEquals(List.of("Site Initiation Visit"), ex.getMissingItems());
        assertEquals(SiteStatus.PENDING_ACTIVATION, site.getStatus());
    }

    @Test
    void attemptActivation_allComplete_promotes() {
        for (ChecklistItemType type : ChecklistItemType.values()) {
            itemOfType(type).setStatus(ChecklistItemStatus.COMPLETE);
        }
        ActivationAttemptResponse response = siteActivationService.attemptActivation(
                100L, new AttemptActivationRequest("correct-password", "all prerequisites met"), "study.manager");
        assertTrue(response.activated());
        assertTrue(response.missingItems().isEmpty());
        assertEquals(SiteStatus.ACTIVE, site.getStatus());
    }

    @Test
    void attemptActivation_alreadyActive_isIdempotent() {
        site.setStatus(SiteStatus.ACTIVE);
        for (ChecklistItemType type : ChecklistItemType.values()) {
            itemOfType(type).setStatus(ChecklistItemStatus.COMPLETE);
        }
        siteActivationService.attemptActivation(100L, new AttemptActivationRequest("correct-password", "reattempt"), "study.manager");
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
        assertEquals(SiteStatus.ACTIVE, site.getStatus());
    }

    @Test
    void attemptActivation_wrongPassword_throwsAndLeavesSitePendingActivation() {
        for (ChecklistItemType type : ChecklistItemType.values()) {
            itemOfType(type).setStatus(ChecklistItemStatus.COMPLETE);
        }
        when(eSignatureService.sign("study.manager", "wrong-password", "Site", "100", "attempting"))
                .thenThrow(new InvalidCredentialsException());

        assertThrows(InvalidCredentialsException.class, () -> siteActivationService.attemptActivation(
                100L, new AttemptActivationRequest("wrong-password", "attempting"), "study.manager"));
        assertEquals(SiteStatus.PENDING_ACTIVATION, site.getStatus());
    }
}
