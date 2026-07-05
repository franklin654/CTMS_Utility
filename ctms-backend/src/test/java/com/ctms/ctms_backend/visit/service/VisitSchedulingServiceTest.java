package com.ctms.ctms_backend.visit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectStatus;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.entity.VisitTemplate;
import com.ctms.ctms_backend.visit.entity.VisitType;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import com.ctms.ctms_backend.visit.repository.VisitTemplateRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitSchedulingServiceTest {

    @Mock private VisitTemplateRepository templateRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private VisitSchedulingService schedulingService;

    @Test
    void generateForSubject_anchorsToScreeningDatePlusTargetDay_orderedBySequence() {
        Study study = new Study();
        study.setId(10L);

        User creator = new User();
        creator.setId(1L);

        Subject subject = new Subject();
        subject.setId(1000L);
        subject.setStudy(study);
        subject.setSubjectCode("SUBJ-001000");
        subject.setScreeningDate(LocalDate.of(2026, 1, 1));
        subject.setCreatedBy(creator);

        VisitTemplate t1 = template(1L, 1, 0, "Screening");
        VisitTemplate t2 = template(2L, 2, 14, "Follow-up");

        when(templateRepository.findByStudyIdAndActiveTrueOrderBySequenceNumber(10L)).thenReturn(List.of(t1, t2));
        when(visitRepository.save(any(Visit.class))).thenAnswer(inv -> {
            Visit v = inv.getArgument(0);
            v.setId(v.getSequenceNumber().longValue());
            return v;
        });

        schedulingService.generateForSubject(subject);

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<Visit> saved = captor.getAllValues();

        assertEquals(LocalDate.of(2026, 1, 1), saved.get(0).getScheduledDate());
        assertEquals(LocalDate.of(2026, 1, 15), saved.get(1).getScheduledDate());
        assertEquals(VisitStatus.SCHEDULED, saved.get(0).getStatus());
        assertEquals(VisitStatus.SCHEDULED, saved.get(1).getStatus());
    }

    @Test
    void generateForNewTemplate_backfillsActiveSubjectsOnly() {
        Study study = new Study();
        study.setId(10L);

        User creator = new User();
        creator.setId(1L);

        Subject active = new Subject();
        active.setId(1000L);
        active.setStudy(study);
        active.setSubjectCode("SUBJ-001000");
        active.setScreeningDate(LocalDate.of(2026, 1, 1));
        active.setCreatedBy(creator);
        active.setStatus(SubjectStatus.ENROLLED);

        VisitTemplate newTemplate = template(3L, 3, 30, "Follow-up 2");
        newTemplate.setStudy(study);

        when(subjectRepository.findByStudyIdAndStatusNotIn(
                        eqStudyId(10L), eqStatuses(SubjectStatus.COMPLETED, SubjectStatus.WITHDRAWN)))
                .thenReturn(List.of(active));
        when(visitRepository.save(any(Visit.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulingService.generateForNewTemplate(newTemplate);

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        org.mockito.Mockito.verify(visitRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 1, 31), captor.getValue().getScheduledDate());
        assertEquals(active, captor.getValue().getSubject());
    }

    private Long eqStudyId(Long studyId) {
        return org.mockito.ArgumentMatchers.eq(studyId);
    }

    private List<SubjectStatus> eqStatuses(SubjectStatus... statuses) {
        return org.mockito.ArgumentMatchers.eq(List.of(statuses));
    }

    private VisitTemplate template(Long id, int sequenceNumber, int targetDay, String name) {
        VisitTemplate t = new VisitTemplate();
        t.setId(id);
        t.setName(name);
        t.setSequenceNumber(sequenceNumber);
        t.setTargetDay(targetDay);
        t.setWindowEarlyDays(1);
        t.setWindowLateDays(1);
        t.setVisitType(VisitType.ONSITE);
        return t;
    }
}
