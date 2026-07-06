package com.ctms.ctms_backend.visit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.UpdateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.VisitTemplateResponse;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.entity.VisitTemplate;
import com.ctms.ctms_backend.visit.exception.CrossStudyDependencyException;
import com.ctms.ctms_backend.visit.exception.VisitTemplateDependencyCycleException;
import com.ctms.ctms_backend.visit.exception.VisitTemplateWindowInvalidException;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import com.ctms.ctms_backend.visit.repository.VisitTemplateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitTemplateServiceTest {

    @Mock private VisitTemplateRepository templateRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private VisitSchedulingService visitSchedulingService;

    @InjectMocks
    private VisitTemplateService templateService;

    private Study study;

    @BeforeEach
    void setUp() {
        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        lenient().when(studyRepository.findById(10L)).thenReturn(Optional.of(study));

        User actor = new User();
        actor.setId(1L);
        actor.setUsername("studymgr1");
        lenient().when(userRepository.findByUsername("studymgr1")).thenReturn(Optional.of(actor));

        lenient().when(templateRepository.save(any(VisitTemplate.class))).thenAnswer(inv -> {
            VisitTemplate t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(500L);
            }
            return t;
        });
    }

    private CreateVisitTemplateRequest validCreateRequest() {
        return new CreateVisitTemplateRequest(10L, "Screening Visit", 1, 0, 2, 3, "Vitals, bloodwork", "ONSITE", null);
    }

    @Test
    void create_negativeTargetDay_throws() {
        CreateVisitTemplateRequest req = new CreateVisitTemplateRequest(10L, "Visit 1", 1, -5, 0, 0, null, "ONSITE", null);
        assertThrows(VisitTemplateWindowInvalidException.class, () -> templateService.create(req, "studymgr1"));
    }

    @Test
    void create_negativeWindow_throws() {
        CreateVisitTemplateRequest req = new CreateVisitTemplateRequest(10L, "Visit 1", 1, 10, -1, 0, null, "ONSITE", null);
        assertThrows(VisitTemplateWindowInvalidException.class, () -> templateService.create(req, "studymgr1"));
    }

    @Test
    void create_happyPath_savesAndAudits() {
        VisitTemplateResponse response = templateService.create(validCreateRequest(), "studymgr1");
        assertEquals("Screening Visit", response.name());
        assertEquals("ONSITE", response.visitType());
        verify(auditService).record(eq("VisitTemplate"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void create_backfillsNewTemplateOntoAlreadyEnrolledSubjects() {
        VisitTemplateResponse response = templateService.create(validCreateRequest(), "studymgr1");

        ArgumentCaptor<VisitTemplate> captor = ArgumentCaptor.forClass(VisitTemplate.class);
        verify(visitSchedulingService).generateForNewTemplate(captor.capture());
        assertEquals(response.id(), captor.getValue().getId());
    }

    @Test
    void update_propagatesToScheduledVisitsOnly() {
        VisitTemplate template = new VisitTemplate();
        template.setId(500L);
        template.setStudy(study);
        template.setName("Old Name");
        template.setSequenceNumber(1);
        template.setTargetDay(5);
        template.setWindowEarlyDays(1);
        template.setWindowLateDays(1);
        when(templateRepository.findById(500L)).thenReturn(Optional.of(template));

        Subject subject = new Subject();
        subject.setId(1L);
        subject.setScreeningDate(LocalDate.of(2026, 1, 1));

        Visit scheduledVisit = new Visit();
        scheduledVisit.setId(1L);
        scheduledVisit.setSubject(subject);
        scheduledVisit.setVisitTemplate(template);
        scheduledVisit.setStatus(VisitStatus.SCHEDULED);
        scheduledVisit.setTargetDay(5);
        scheduledVisit.setScheduledDate(LocalDate.of(2026, 1, 6));

        when(visitRepository.findByVisitTemplateIdAndStatus(500L, VisitStatus.SCHEDULED))
                .thenReturn(List.of(scheduledVisit));
        when(visitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateVisitTemplateRequest req = new UpdateVisitTemplateRequest("New Name", 1, 10, 1, 1, "Procedures", "REMOTE", null);
        templateService.update(500L, req, "studymgr1");

        assertEquals("New Name", scheduledVisit.getName());
        assertEquals(10, scheduledVisit.getTargetDay());
        assertEquals(LocalDate.of(2026, 1, 11), scheduledVisit.getScheduledDate());

        ArgumentCaptor<List<Visit>> captor = ArgumentCaptor.forClass(List.class);
        verify(visitRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());

        verify(auditService).record(
                eq("Visit"), eq("1"), eq(AuditAction.UPDATE),
                eq("2026-01-06"), eq("2026-01-11"), anyString());
    }

    @Test
    void create_withDependencyInSameStudy_succeeds() {
        VisitTemplate prerequisite = new VisitTemplate();
        prerequisite.setId(400L);
        prerequisite.setStudy(study);
        when(templateRepository.findById(400L)).thenReturn(Optional.of(prerequisite));

        CreateVisitTemplateRequest req = new CreateVisitTemplateRequest(10L, "Visit 2", 2, 14, 1, 1, null, "ONSITE", 400L);
        VisitTemplateResponse response = templateService.create(req, "studymgr1");

        assertEquals(400L, response.dependsOnVisitTemplateId());
    }

    @Test
    void create_withDependencyInDifferentStudy_throws() {
        Study otherStudy = new Study();
        otherStudy.setId(99L);
        VisitTemplate prerequisite = new VisitTemplate();
        prerequisite.setId(400L);
        prerequisite.setStudy(otherStudy);
        when(templateRepository.findById(400L)).thenReturn(Optional.of(prerequisite));

        CreateVisitTemplateRequest req = new CreateVisitTemplateRequest(10L, "Visit 2", 2, 14, 1, 1, null, "ONSITE", 400L);
        assertThrows(CrossStudyDependencyException.class, () -> templateService.create(req, "studymgr1"));
    }

    @Test
    void update_dependencyWouldCreateCycle_throws() {
        VisitTemplate templateA = new VisitTemplate();
        templateA.setId(500L);
        templateA.setStudy(study);

        VisitTemplate templateB = new VisitTemplate();
        templateB.setId(501L);
        templateB.setStudy(study);
        templateB.setDependsOnVisitTemplate(templateA);

        when(templateRepository.findById(500L)).thenReturn(Optional.of(templateA));
        when(templateRepository.findById(501L)).thenReturn(Optional.of(templateB));

        // A depends on B, but B already depends on A -- a cycle.
        UpdateVisitTemplateRequest req = new UpdateVisitTemplateRequest("Visit A", 1, 0, 1, 1, null, "ONSITE", 501L);
        assertThrows(VisitTemplateDependencyCycleException.class, () -> templateService.update(500L, req, "studymgr1"));
    }
}
