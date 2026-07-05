package com.ctms.ctms_backend.visit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitAlertServiceTest {

    @Mock private com.ctms.ctms_backend.visit.repository.VisitRepository visitRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private VisitAlertService visitAlertService;

    private Subject subject;

    @BeforeEach
    void setUp() {
        User creator = new User();
        creator.setId(1L);

        Site site = new Site();
        site.setId(20L);

        subject = new Subject();
        subject.setId(1000L);
        subject.setSubjectCode("SUBJ-001000");
        subject.setSite(site);
        subject.setCreatedBy(creator);
    }

    private Visit visit(Long id, LocalDate scheduledDate, int windowLateDays) {
        Visit v = new Visit();
        v.setId(id);
        v.setSubject(subject);
        v.setName("Follow-up Visit");
        v.setScheduledDate(scheduledDate);
        v.setWindowLateDays(windowLateDays);
        v.setStatus(VisitStatus.SCHEDULED);
        return v;
    }

    @Test
    void dueTomorrowVisit_sendsAlertOnce() {
        Visit v = visit(1L, LocalDate.now().plusDays(1), 1);
        when(visitRepository.findByStatusAndScheduledDate(eq(VisitStatus.SCHEDULED), eq(LocalDate.now().plusDays(1))))
                .thenReturn(List.of(v));
        when(visitRepository.findByStatusAndScheduledDateLessThan(eq(VisitStatus.SCHEDULED), any())).thenReturn(List.of());
        when(notificationService.alreadyNotified(eq(1L), eq("VISIT_DUE_TOMORROW"), anyString())).thenReturn(false);

        visitAlertService.runDailyAlertSweep();

        verify(notificationService).notify(eq(1L), eq("VISIT_DUE_TOMORROW"), anyString(), anyString(), anyString());
    }

    @Test
    void dueTomorrowVisit_alreadyNotified_doesNotResend() {
        Visit v = visit(1L, LocalDate.now().plusDays(1), 1);
        when(visitRepository.findByStatusAndScheduledDate(eq(VisitStatus.SCHEDULED), eq(LocalDate.now().plusDays(1))))
                .thenReturn(List.of(v));
        when(visitRepository.findByStatusAndScheduledDateLessThan(eq(VisitStatus.SCHEDULED), any())).thenReturn(List.of());
        when(notificationService.alreadyNotified(eq(1L), eq("VISIT_DUE_TOMORROW"), anyString())).thenReturn(true);

        visitAlertService.runDailyAlertSweep();

        verify(notificationService, never()).notify(eq(1L), eq("VISIT_DUE_TOMORROW"), anyString(), anyString(), anyString());
    }

    @Test
    void pastLateWindow_sendsOverdueAlert() {
        Visit v = visit(2L, LocalDate.now().minusDays(5), 1);
        when(visitRepository.findByStatusAndScheduledDate(eq(VisitStatus.SCHEDULED), any())).thenReturn(List.of());
        when(visitRepository.findByStatusAndScheduledDateLessThan(eq(VisitStatus.SCHEDULED), eq(LocalDate.now())))
                .thenReturn(List.of(v));
        when(notificationService.alreadyNotified(eq(1L), eq("VISIT_OVERDUE"), anyString())).thenReturn(false);

        visitAlertService.runDailyAlertSweep();

        verify(notificationService).notify(eq(1L), eq("VISIT_OVERDUE"), anyString(), anyString(), anyString());
    }

    @Test
    void withinLateWindow_noOverdueAlert() {
        Visit v = visit(3L, LocalDate.now().minusDays(1), 5);
        when(visitRepository.findByStatusAndScheduledDate(eq(VisitStatus.SCHEDULED), any())).thenReturn(List.of());
        when(visitRepository.findByStatusAndScheduledDateLessThan(eq(VisitStatus.SCHEDULED), eq(LocalDate.now())))
                .thenReturn(List.of(v));

        visitAlertService.runDailyAlertSweep();

        verify(notificationService, never()).notify(eq(1L), eq("VISIT_OVERDUE"), anyString(), anyString(), anyString());
    }
}
