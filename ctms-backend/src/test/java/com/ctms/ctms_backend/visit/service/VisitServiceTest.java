package com.ctms.ctms_backend.visit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.payment.service.PaymentService;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.dto.CreateAdHocVisitRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitCompletedRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitMissedRequest;
import com.ctms.ctms_backend.visit.dto.RescheduleVisitRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.dto.VisitResponse;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.entity.VisitTemplate;
import com.ctms.ctms_backend.visit.entity.VisitType;
import com.ctms.ctms_backend.visit.exception.InvalidVisitTransitionException;
import com.ctms.ctms_backend.visit.exception.VisitDependencyNotMetException;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock private VisitRepository visitRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private TaskService taskService;
    @Mock private PaymentService paymentService;

    @InjectMocks
    private VisitService visitService;

    private Subject subject;
    private Visit scheduledVisit;
    private User actor;

    @BeforeEach
    void setUp() {
        User creator = new User();
        creator.setId(1L);

        Site site = new Site();
        site.setId(20L);
        site.setSiteCode("SITE-001");

        Study study = new Study();
        study.setId(10L);
        study.setCreatedBy(creator);

        subject = new Subject();
        subject.setId(1000L);
        subject.setSubjectCode("SUBJ-001000");
        subject.setSite(site);
        subject.setStudy(study);
        subject.setCreatedBy(creator);
        subject.setScreeningDate(LocalDate.now().minusDays(30));

        actor = new User();
        actor.setId(2L);
        actor.setUsername("coordinator1");
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));
        lenient().when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));

        VisitTemplate template = new VisitTemplate();
        template.setId(500L);
        template.setVisitType(VisitType.ONSITE);

        scheduledVisit = new Visit();
        scheduledVisit.setId(1L);
        scheduledVisit.setSubject(subject);
        scheduledVisit.setVisitTemplate(template);
        scheduledVisit.setName("Screening Visit");
        scheduledVisit.setSequenceNumber(1);
        scheduledVisit.setTargetDay(0);
        scheduledVisit.setWindowEarlyDays(1);
        scheduledVisit.setWindowLateDays(1);
        scheduledVisit.setVisitType(VisitType.ONSITE);
        scheduledVisit.setScheduledDate(LocalDate.now());
        scheduledVisit.setStatus(VisitStatus.SCHEDULED);
        scheduledVisit.setCreatedBy(creator);

        lenient().when(visitRepository.findById(1L)).thenReturn(Optional.of(scheduledVisit));
        lenient().when(visitRepository.save(any(Visit.class))).thenAnswer(inv -> {
            Visit v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(2L);
            }
            return v;
        });
    }

    @Test
    void markCompleted_setsStatusAndTimestamps() {
        MarkVisitCompletedRequest req = new MarkVisitCompletedRequest(LocalDate.now(), null, "all good");
        VisitResponse response = visitService.markCompleted(1L, req, "coordinator1");
        assertEquals("COMPLETED", response.status());
    }

    @Test
    void markCompleted_notifiesLinkedPatientAccount() {
        com.ctms.ctms_backend.user.User patientUser = new com.ctms.ctms_backend.user.User();
        patientUser.setId(77L);
        subject.setLinkedUser(patientUser);

        MarkVisitCompletedRequest req = new MarkVisitCompletedRequest(LocalDate.now(), null, "all good");
        visitService.markCompleted(1L, req, "coordinator1");

        org.mockito.Mockito.verify(notificationService).notify(
                org.mockito.ArgumentMatchers.eq(77L), org.mockito.ArgumentMatchers.eq("VISIT_COMPLETED"),
                any(), any(), any());
    }

    @Test
    void markCompleted_alreadyCompleted_throws() {
        scheduledVisit.setStatus(VisitStatus.COMPLETED);
        MarkVisitCompletedRequest req = new MarkVisitCompletedRequest(LocalDate.now(), null, null);
        assertThrows(InvalidVisitTransitionException.class, () -> visitService.markCompleted(1L, req, "coordinator1"));
    }

    @Test
    void markCompleted_prerequisiteNotCompleted_throws() {
        VisitTemplate prerequisite = new VisitTemplate();
        prerequisite.setId(400L);
        prerequisite.setName("Screening Visit");
        scheduledVisit.getVisitTemplate().setDependsOnVisitTemplate(prerequisite);

        Visit prerequisiteVisit = new Visit();
        prerequisiteVisit.setId(99L);
        prerequisiteVisit.setStatus(VisitStatus.SCHEDULED);
        when(visitRepository.findBySubjectIdAndVisitTemplateId(1000L, 400L)).thenReturn(Optional.of(prerequisiteVisit));

        MarkVisitCompletedRequest req = new MarkVisitCompletedRequest(LocalDate.now(), null, null);
        assertThrows(VisitDependencyNotMetException.class, () -> visitService.markCompleted(1L, req, "coordinator1"));
    }

    @Test
    void markCompleted_prerequisiteNotEvenScheduled_throws() {
        VisitTemplate prerequisite = new VisitTemplate();
        prerequisite.setId(400L);
        prerequisite.setName("Screening Visit");
        scheduledVisit.getVisitTemplate().setDependsOnVisitTemplate(prerequisite);

        when(visitRepository.findBySubjectIdAndVisitTemplateId(1000L, 400L)).thenReturn(Optional.empty());

        MarkVisitCompletedRequest req = new MarkVisitCompletedRequest(LocalDate.now(), null, null);
        assertThrows(VisitDependencyNotMetException.class, () -> visitService.markCompleted(1L, req, "coordinator1"));
    }

    @Test
    void markCompleted_prerequisiteCompleted_succeeds() {
        VisitTemplate prerequisite = new VisitTemplate();
        prerequisite.setId(400L);
        prerequisite.setName("Screening Visit");
        scheduledVisit.getVisitTemplate().setDependsOnVisitTemplate(prerequisite);

        Visit prerequisiteVisit = new Visit();
        prerequisiteVisit.setId(99L);
        prerequisiteVisit.setStatus(VisitStatus.COMPLETED);
        when(visitRepository.findBySubjectIdAndVisitTemplateId(1000L, 400L)).thenReturn(Optional.of(prerequisiteVisit));

        MarkVisitCompletedRequest req = new MarkVisitCompletedRequest(LocalDate.now(), null, "all good");
        VisitResponse response = visitService.markCompleted(1L, req, "coordinator1");
        assertEquals("COMPLETED", response.status());
    }

    @Test
    void markMissed_setsStatusAndReasonCode() {
        MarkVisitMissedRequest req = new MarkVisitMissedRequest("Subject unreachable");
        VisitResponse response = visitService.markMissed(1L, req, "coordinator1");
        assertEquals("MISSED", response.status());
        assertEquals("Subject unreachable", response.reasonCode());
    }

    @Test
    void reschedule_createsLinkedNewVisitAndMarksOriginalRescheduled() {
        RescheduleVisitRequest req = new RescheduleVisitRequest(LocalDate.now().plusDays(7), "Subject requested change");
        VisitResponse response = visitService.reschedule(1L, req);

        assertEquals("SCHEDULED", response.status());
        assertEquals(1L, response.rescheduledFromVisitId());
        assertEquals(VisitStatus.RESCHEDULED, scheduledVisit.getStatus());
        assertEquals("Subject requested change", scheduledVisit.getReasonCode());
    }

    @Test
    void reschedule_nonScheduledVisit_throws() {
        scheduledVisit.setStatus(VisitStatus.MISSED);
        RescheduleVisitRequest req = new RescheduleVisitRequest(LocalDate.now().plusDays(7), "reason");
        assertThrows(InvalidVisitTransitionException.class, () -> visitService.reschedule(1L, req));
    }

    @Test
    void schedule_complianceRate_reflectsCompletedAndMissedMix() {
        VisitTemplate template = new VisitTemplate();
        template.setId(500L);

        Visit completed = new Visit();
        completed.setId(2L);
        completed.setSubject(subject);
        completed.setVisitTemplate(template);
        completed.setVisitType(VisitType.ONSITE);
        completed.setSequenceNumber(1);
        completed.setScheduledDate(LocalDate.now().minusDays(10));
        completed.setStatus(VisitStatus.COMPLETED);

        Visit missed = new Visit();
        missed.setId(3L);
        missed.setSubject(subject);
        missed.setVisitTemplate(template);
        missed.setVisitType(VisitType.ONSITE);
        missed.setSequenceNumber(2);
        missed.setScheduledDate(LocalDate.now().minusDays(5));
        missed.setStatus(VisitStatus.MISSED);

        Visit futureScheduled = new Visit();
        futureScheduled.setId(4L);
        futureScheduled.setSubject(subject);
        futureScheduled.setVisitTemplate(template);
        futureScheduled.setVisitType(VisitType.ONSITE);
        futureScheduled.setSequenceNumber(3);
        futureScheduled.setScheduledDate(LocalDate.now().plusDays(10));
        futureScheduled.setStatus(VisitStatus.SCHEDULED);

        when(subjectRepository.existsById(1000L)).thenReturn(true);
        when(visitRepository.findBySubjectIdOrderByScheduledDateAsc(1000L))
                .thenReturn(List.of(completed, missed, futureScheduled));

        SubjectVisitScheduleResponse response = visitService.schedule(1000L);
        assertEquals(3, response.visits().size());
        assertEquals(0.5, response.complianceRate());
    }

    @Test
    void scheduleAdHoc_createsVisitWithNullTemplate() {
        CreateAdHocVisitRequest req = new CreateAdHocVisitRequest(
                "AE Follow-up", LocalDate.now().plusDays(3), "ONSITE", "Vitals recheck", "Reported mild AE");

        VisitResponse response = visitService.scheduleAdHoc(1000L, req, "coordinator1");

        assertEquals("AE Follow-up", response.name());
        assertEquals("SCHEDULED", response.status());
        assertEquals(true, response.adHoc());
        assertEquals(null, response.visitTemplateId());
        assertEquals("Reported mild AE", response.reasonCode());
    }

    @Test
    void schedule_complianceRate_excludesAdHocVisits() {
        Visit adHocMissed = new Visit();
        adHocMissed.setId(5L);
        adHocMissed.setSubject(subject);
        adHocMissed.setVisitTemplate(null);
        adHocMissed.setVisitType(VisitType.ONSITE);
        adHocMissed.setScheduledDate(LocalDate.now().minusDays(2));
        adHocMissed.setStatus(VisitStatus.MISSED);

        VisitTemplate template = new VisitTemplate();
        template.setId(500L);

        Visit completed = new Visit();
        completed.setId(6L);
        completed.setSubject(subject);
        completed.setVisitTemplate(template);
        completed.setVisitType(VisitType.ONSITE);
        completed.setScheduledDate(LocalDate.now().minusDays(10));
        completed.setStatus(VisitStatus.COMPLETED);

        when(subjectRepository.existsById(1000L)).thenReturn(true);
        when(visitRepository.findBySubjectIdOrderByScheduledDateAsc(1000L))
                .thenReturn(List.of(completed, adHocMissed));

        SubjectVisitScheduleResponse response = visitService.schedule(1000L);
        assertEquals(1.0, response.complianceRate());
    }
}
