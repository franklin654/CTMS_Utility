package com.ctms.ctms_backend.milestone.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.milestone.entity.Milestone;
import com.ctms.ctms_backend.milestone.entity.MilestoneType;
import com.ctms.ctms_backend.milestone.repository.MilestoneRepository;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.user.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MilestoneAlertServiceTest {

    @Mock private MilestoneRepository milestoneRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private MilestoneAlertService milestoneAlertService;

    @Test
    void runDailyAlertSweep_notifiesStudyManagerWhenNotAlreadyNotified() {
        User manager = new User();
        manager.setId(1L);
        manager.setUsername("study.manager");

        Study study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        study.setCreatedBy(manager);

        Milestone milestone = new Milestone();
        milestone.setId(400L);
        milestone.setStudy(study);
        milestone.setMilestoneType(MilestoneType.FPI);
        milestone.setPlannedDate(LocalDate.now().plusDays(3));

        when(milestoneRepository.findByActualDateIsNullAndPlannedDateBetween(any(), any())).thenReturn(List.of(milestone));
        when(notificationService.alreadyNotified(eq(1L), eq("MILESTONE_NEARING_DEADLINE"), any())).thenReturn(false);

        milestoneAlertService.runDailyAlertSweep();

        verify(notificationService, times(1)).notify(eq(1L), eq("MILESTONE_NEARING_DEADLINE"), any(), any(), any());
    }

    @Test
    void runDailyAlertSweep_dedupSkipsAlreadyNotified() {
        User manager = new User();
        manager.setId(1L);
        manager.setUsername("study.manager");

        Study study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        study.setCreatedBy(manager);

        Milestone milestone = new Milestone();
        milestone.setId(400L);
        milestone.setStudy(study);
        milestone.setMilestoneType(MilestoneType.FPI);
        milestone.setPlannedDate(LocalDate.now().plusDays(3));

        when(milestoneRepository.findByActualDateIsNullAndPlannedDateBetween(any(), any())).thenReturn(List.of(milestone));
        when(notificationService.alreadyNotified(eq(1L), eq("MILESTONE_NEARING_DEADLINE"), any())).thenReturn(true);

        milestoneAlertService.runDailyAlertSweep();

        verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    }
}
