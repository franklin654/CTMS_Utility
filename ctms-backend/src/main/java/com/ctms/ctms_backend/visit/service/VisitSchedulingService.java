package com.ctms.ctms_backend.visit.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectStatus;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.entity.VisitTemplate;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import com.ctms.ctms_backend.visit.repository.VisitTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates a Subject's full visit schedule at enrollment time (BRD Epic 4 Story 01 AC3: "Visit
 * schedule applies automatically to all enrolled subjects"), anchoring each visit's scheduledDate
 * to the subject's (locked, always-present) screeningDate + the template's targetDay. Called from
 * SubjectService.enrollSubject right after a successful enrollment.
 *
 * <p>Also backfills a newly-created template onto already-enrolled subjects (a gap the BRD itself
 * doesn't address -- Story 01 only describes propagation on template *edits*, not on adding a
 * template after subjects already exist). Confirmed design: backfill only subjects still active
 * in the trial (SCREENED/ENROLLED/IN_TREATMENT) -- a COMPLETED or WITHDRAWN subject's participation
 * is formally over, so retroactively adding a visit to their record would be the wrong side of the
 * "don't alter historical/closed data" principle. */
@Service
public class VisitSchedulingService {

    private static final List<SubjectStatus> TERMINAL_STATUSES =
            List.of(SubjectStatus.COMPLETED, SubjectStatus.WITHDRAWN);

    private final VisitTemplateRepository templateRepository;
    private final VisitRepository visitRepository;
    private final SubjectRepository subjectRepository;
    private final AuditService auditService;

    public VisitSchedulingService(
            VisitTemplateRepository templateRepository,
            VisitRepository visitRepository,
            SubjectRepository subjectRepository,
            AuditService auditService) {
        this.templateRepository = templateRepository;
        this.visitRepository = visitRepository;
        this.subjectRepository = subjectRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void generateForSubject(Subject subject) {
        List<VisitTemplate> templates =
                templateRepository.findByStudyIdAndActiveTrueOrderBySequenceNumber(subject.getStudy().getId());
        for (VisitTemplate template : templates) {
            createVisit(subject, template);
        }
    }

    @Transactional
    public void generateForNewTemplate(VisitTemplate template) {
        List<Subject> activeSubjects =
                subjectRepository.findByStudyIdAndStatusNotIn(template.getStudy().getId(), TERMINAL_STATUSES);
        for (Subject subject : activeSubjects) {
            createVisit(subject, template);
        }
    }

    private void createVisit(Subject subject, VisitTemplate template) {
        Visit visit = new Visit();
        visit.setSubject(subject);
        visit.setVisitTemplate(template);
        visit.setName(template.getName());
        visit.setSequenceNumber(template.getSequenceNumber());
        visit.setTargetDay(template.getTargetDay());
        visit.setWindowEarlyDays(template.getWindowEarlyDays());
        visit.setWindowLateDays(template.getWindowLateDays());
        visit.setRequiredProcedures(template.getRequiredProcedures());
        visit.setVisitType(template.getVisitType());
        visit.setScheduledDate(subject.getScreeningDate().plusDays(template.getTargetDay()));
        visit.setStatus(VisitStatus.SCHEDULED);
        visit.setCreatedBy(subject.getCreatedBy());
        visit.setModifiedBy(subject.getCreatedBy());
        visit = visitRepository.save(visit);

        auditService.record(
                "Visit", String.valueOf(visit.getId()), AuditAction.CREATE,
                null, "scheduled " + visit.getName() + " for " + visit.getScheduledDate()
                        + " (subject " + subject.getSubjectCode() + ")", null);
    }
}
