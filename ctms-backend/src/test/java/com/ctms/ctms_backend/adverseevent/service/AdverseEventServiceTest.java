package com.ctms.ctms_backend.adverseevent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.adverseevent.dto.ReportAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.ResolveAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.TransitionAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEvent;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventStatus;
import com.ctms.ctms_backend.adverseevent.exception.InvalidAdverseEventTransitionException;
import com.ctms.ctms_backend.adverseevent.repository.AdverseEventRepository;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdverseEventServiceTest {

    @Mock private AdverseEventRepository adverseEventRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private TaskService taskService;
    @Mock private ESignatureService eSignatureService;

    @InjectMocks
    private AdverseEventService adverseEventService;

    private Subject subject;
    private User actor;
    private AdverseEvent openEvent;

    @BeforeEach
    void setUp() {
        Study study = new Study();
        study.setId(10L);
        study.setCreatedBy(new User());
        study.getCreatedBy().setId(2L);
        study.getCreatedBy().setUsername("studymgr1");

        Site site = new Site();
        site.setId(20L);

        actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");

        subject = new Subject();
        subject.setId(1000L);
        subject.setSubjectCode("SUBJ-001000");
        subject.setSite(site);
        subject.setStudy(study);
        subject.setCreatedBy(actor);

        lenient().when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));
        lenient().when(adverseEventRepository.save(any(AdverseEvent.class))).thenAnswer(inv -> {
            AdverseEvent ae = inv.getArgument(0);
            if (ae.getId() == null) {
                ae.setId(300L);
            }
            return ae;
        });

        openEvent = new AdverseEvent();
        openEvent.setId(300L);
        openEvent.setSubject(subject);
        openEvent.setStatus(AdverseEventStatus.OPEN);
        openEvent.setSeverity(com.ctms.ctms_backend.adverseevent.entity.AdverseEventSeverity.MODERATE);
        openEvent.setCreatedBy(actor);
        lenient().when(adverseEventRepository.findById(300L)).thenReturn(Optional.of(openEvent));
    }

    @Test
    void report_mildSeverity_doesNotCreateTask() {
        ReportAdverseEventRequest req = new ReportAdverseEventRequest(1000L, null, "Mild headache", "MILD");
        AdverseEventResponse response = adverseEventService.report(req, "coordinator1");

        assertEquals("MILD", response.severity());
        assertEquals("OPEN", response.status());
        verify(taskService, never()).createTask(any(), any(), any(), any(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void report_severeSeverity_createsEscalationTask() {
        ReportAdverseEventRequest req = new ReportAdverseEventRequest(1000L, null, "Severe reaction", "SEVERE");
        adverseEventService.report(req, "coordinator1");

        verify(taskService).createTask(
                eq("ADVERSE_EVENT_HIGH_SEVERITY"), anyString(), anyString(), eq("AdverseEvent"),
                anyLong(), eq(1L), eq(2L), eq("coordinator1"));
    }

    @Test
    void report_lifeThreateningSeverity_createsEscalationTask() {
        ReportAdverseEventRequest req = new ReportAdverseEventRequest(1000L, null, "Anaphylaxis", "LIFE_THREATENING");
        adverseEventService.report(req, "coordinator1");

        verify(taskService, times(1)).createTask(any(), any(), any(), any(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void transition_openToUnderReview_succeeds() {
        TransitionAdverseEventRequest req = new TransitionAdverseEventRequest("UNDER_REVIEW", "reviewing now");
        AdverseEventResponse response = adverseEventService.transition(300L, req, "coordinator1");
        assertEquals("UNDER_REVIEW", response.status());
    }

    @Test
    void transition_directlyToResolved_throws() {
        TransitionAdverseEventRequest req = new TransitionAdverseEventRequest("RESOLVED", "skip ahead");
        assertThrows(InvalidAdverseEventTransitionException.class, () -> adverseEventService.transition(300L, req, "coordinator1"));
    }

    @Test
    void resolve_fromUnderReview_succeeds() {
        openEvent.setStatus(AdverseEventStatus.UNDER_REVIEW);
        when(eSignatureService.sign("coordinator1", "correct-password", "AdverseEvent", "300", "Explained and resolved"))
                .thenReturn(new ESignature(actor, "AdverseEvent", "300", "Explained and resolved"));

        ResolveAdverseEventRequest req = new ResolveAdverseEventRequest("Explained and resolved", "correct-password");
        AdverseEventResponse response = adverseEventService.resolve(300L, req, "coordinator1");
        assertEquals("RESOLVED", response.status());
        assertEquals("Explained and resolved", response.resolutionNotes());
    }

    @Test
    void resolve_fromOpen_throws() {
        ResolveAdverseEventRequest req = new ResolveAdverseEventRequest("too early", "correct-password");
        assertThrows(InvalidAdverseEventTransitionException.class, () -> adverseEventService.resolve(300L, req, "coordinator1"));
    }

    @Test
    void resolve_wrongPassword_throwsAndLeavesStatusUntouched() {
        openEvent.setStatus(AdverseEventStatus.UNDER_REVIEW);
        when(eSignatureService.sign("coordinator1", "wrong-password", "AdverseEvent", "300", "Explained and resolved"))
                .thenThrow(new InvalidCredentialsException());

        ResolveAdverseEventRequest req = new ResolveAdverseEventRequest("Explained and resolved", "wrong-password");
        assertThrows(InvalidCredentialsException.class, () -> adverseEventService.resolve(300L, req, "coordinator1"));
        assertEquals(AdverseEventStatus.UNDER_REVIEW, openEvent.getStatus());
    }
}
