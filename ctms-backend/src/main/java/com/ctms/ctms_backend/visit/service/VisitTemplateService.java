package com.ctms.ctms_backend.visit.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.UpdateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.VisitTemplateResponse;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.entity.VisitTemplate;
import com.ctms.ctms_backend.visit.entity.VisitType;
import com.ctms.ctms_backend.visit.exception.VisitTemplateNotFoundException;
import com.ctms.ctms_backend.visit.exception.VisitTemplateWindowInvalidException;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import com.ctms.ctms_backend.visit.repository.VisitTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BRD Epic 4 Story 01 (Configure Visit Schedules) + Epic 9 Story 02. Editing a template updates
 * in place any of its still-SCHEDULED visit rows (already-enrolled subjects' pending visits),
 * recomputing scheduledDate from each subject's screeningDate -- terminal-status rows
 * (COMPLETED/MISSED/RESCHEDULED) are never touched, per AC4 "without losing historical visit data".
 * Creating a brand-new template also backfills a visit onto every already-enrolled, still-active
 * subject in the study (a gap the BRD itself doesn't cover -- see VisitSchedulingService). */
@Service
public class VisitTemplateService {

    private final VisitTemplateRepository templateRepository;
    private final VisitRepository visitRepository;
    private final StudyRepository studyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final VisitSchedulingService visitSchedulingService;

    public VisitTemplateService(
            VisitTemplateRepository templateRepository,
            VisitRepository visitRepository,
            StudyRepository studyRepository,
            UserRepository userRepository,
            AuditService auditService,
            VisitSchedulingService visitSchedulingService) {
        this.templateRepository = templateRepository;
        this.visitRepository = visitRepository;
        this.studyRepository = studyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.visitSchedulingService = visitSchedulingService;
    }

    @Transactional
    public VisitTemplateResponse create(CreateVisitTemplateRequest req, String actorUsername) {
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        validateWindow(req.targetDay(), req.windowEarlyDays(), req.windowLateDays());
        VisitType type = parseType(req.visitType());
        User actor = currentUser(actorUsername);

        VisitTemplate template = new VisitTemplate();
        template.setStudy(study);
        template.setName(req.name());
        template.setSequenceNumber(req.sequenceNumber());
        template.setTargetDay(req.targetDay());
        template.setWindowEarlyDays(req.windowEarlyDays());
        template.setWindowLateDays(req.windowLateDays());
        template.setRequiredProcedures(req.requiredProcedures());
        template.setVisitType(type);
        template.setActive(true);
        template.setCreatedBy(actor);
        template.setModifiedBy(actor);
        template = templateRepository.save(template);

        auditService.record(
                "VisitTemplate", String.valueOf(template.getId()), AuditAction.CREATE,
                null, template.getName() + " (day " + template.getTargetDay() + ", study " + study.getStudyCode() + ")", null);

        visitSchedulingService.generateForNewTemplate(template);

        return VisitTemplateResponse.from(template);
    }

    @Transactional
    public VisitTemplateResponse update(Long id, UpdateVisitTemplateRequest req, String actorUsername) {
        VisitTemplate template = findTemplate(id);
        validateWindow(req.targetDay(), req.windowEarlyDays(), req.windowLateDays());
        VisitType type = parseType(req.visitType());
        User actor = currentUser(actorUsername);

        template.setName(req.name());
        template.setSequenceNumber(req.sequenceNumber());
        template.setTargetDay(req.targetDay());
        template.setWindowEarlyDays(req.windowEarlyDays());
        template.setWindowLateDays(req.windowLateDays());
        template.setRequiredProcedures(req.requiredProcedures());
        template.setVisitType(type);
        template.setModifiedBy(actor);
        template = templateRepository.save(template);

        propagateToScheduledVisits(template, actor);

        auditService.record("VisitTemplate", String.valueOf(id), AuditAction.UPDATE, null, "template fields updated", null);
        return VisitTemplateResponse.from(template);
    }

    @Transactional(readOnly = true)
    public List<VisitTemplateResponse> list(Long studyId) {
        return templateRepository.findByStudyIdAndActiveTrueOrderBySequenceNumber(studyId).stream()
                .map(VisitTemplateResponse::from)
                .toList();
    }

    @Transactional
    public VisitTemplateResponse deactivate(Long id, String actorUsername) {
        VisitTemplate template = findTemplate(id);
        template.setActive(false);
        template.setModifiedBy(currentUser(actorUsername));
        template = templateRepository.save(template);

        auditService.record("VisitTemplate", String.valueOf(id), AuditAction.UPDATE, "active", "inactive", null);
        return VisitTemplateResponse.from(template);
    }

    private void propagateToScheduledVisits(VisitTemplate template, User actor) {
        List<Visit> scheduled = visitRepository.findByVisitTemplateIdAndStatus(template.getId(), VisitStatus.SCHEDULED);
        for (Visit visit : scheduled) {
            visit.setName(template.getName());
            visit.setSequenceNumber(template.getSequenceNumber());
            visit.setTargetDay(template.getTargetDay());
            visit.setWindowEarlyDays(template.getWindowEarlyDays());
            visit.setWindowLateDays(template.getWindowLateDays());
            visit.setRequiredProcedures(template.getRequiredProcedures());
            visit.setVisitType(template.getVisitType());
            visit.setScheduledDate(visit.getSubject().getScreeningDate().plusDays(template.getTargetDay()));
            visit.setModifiedBy(actor);
        }
        visitRepository.saveAll(scheduled);
    }

    VisitTemplate findTemplate(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> new VisitTemplateNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private void validateWindow(int targetDay, int windowEarlyDays, int windowLateDays) {
        if (targetDay < 0) {
            throw new VisitTemplateWindowInvalidException("targetDay must be >= 0");
        }
        if (windowEarlyDays < 0 || windowLateDays < 0) {
            throw new VisitTemplateWindowInvalidException("windowEarlyDays/windowLateDays must be >= 0");
        }
    }

    private VisitType parseType(String value) {
        try {
            return VisitType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new VisitTemplateWindowInvalidException("Unknown visit type: " + value);
        }
    }
}
