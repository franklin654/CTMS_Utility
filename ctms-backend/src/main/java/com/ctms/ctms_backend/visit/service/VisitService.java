package com.ctms.ctms_backend.visit.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
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
import com.ctms.ctms_backend.visit.entity.VisitType;
import com.ctms.ctms_backend.visit.exception.InvalidVisitTransitionException;
import com.ctms.ctms_backend.visit.exception.VisitNotFoundException;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BRD Epic 4 Stories 02 (Track Completed/Missed Visits) & 04 (View Schedule/History), plus an
 * ad-hoc (unscheduled) visit capability beyond the literal BRD -- real trials routinely need a
 * one-off visit outside the protocol schedule (AE follow-up, dose-modification check). Every
 * mutating action here guards status == SCHEDULED (Visit's only non-terminal state) and throws
 * {@link InvalidVisitTransitionException} otherwise -- guarded-transition pattern, not a raw
 * enum setter, mirroring SubjectLifecycleService/StudyService. */
@Service
public class VisitService {

    private final VisitRepository visitRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public VisitService(
            VisitRepository visitRepository,
            SubjectRepository subjectRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.visitRepository = visitRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public SubjectVisitScheduleResponse schedule(Long subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new SubjectNotFoundException(subjectId);
        }
        List<Visit> visits = visitRepository.findBySubjectIdOrderByScheduledDateAsc(subjectId);
        List<VisitResponse> responses = visits.stream().map(VisitResponse::from).toList();
        return new SubjectVisitScheduleResponse(responses, complianceRate(visits));
    }

    @Transactional
    public VisitResponse markCompleted(Long id, MarkVisitCompletedRequest req) {
        Visit visit = findVisit(id);
        guardScheduled(visit);

        visit.setStatus(VisitStatus.COMPLETED);
        visit.setActualDate(req.actualDate());
        visit.setActualTime(req.actualTime());
        visit.setNotes(req.notes());
        visit.setCompletedAt(Instant.now());
        visit = visitRepository.save(visit);

        auditService.record(
                "Visit", String.valueOf(id), AuditAction.STATE_CHANGE, VisitStatus.SCHEDULED.name(), VisitStatus.COMPLETED.name(), null);
        notificationService.clearByLink(visitLink(visit));

        return VisitResponse.from(visit);
    }

    @Transactional
    public VisitResponse markMissed(Long id, MarkVisitMissedRequest req) {
        Visit visit = findVisit(id);
        guardScheduled(visit);

        visit.setStatus(VisitStatus.MISSED);
        visit.setReasonCode(req.reasonCode());
        visit = visitRepository.save(visit);

        auditService.record(
                "Visit", String.valueOf(id), AuditAction.STATE_CHANGE, VisitStatus.SCHEDULED.name(), VisitStatus.MISSED.name(), req.reasonCode());
        notificationService.clearByLink(visitLink(visit));
        notifyVisitMissed(visit);

        return VisitResponse.from(visit);
    }

    @Transactional
    public VisitResponse reschedule(Long id, RescheduleVisitRequest req) {
        Visit original = findVisit(id);
        guardScheduled(original);

        original.setStatus(VisitStatus.RESCHEDULED);
        original.setReasonCode(req.reasonCode());
        original = visitRepository.save(original);

        Visit replacement = new Visit();
        replacement.setSubject(original.getSubject());
        replacement.setVisitTemplate(original.getVisitTemplate());
        replacement.setName(original.getName());
        replacement.setSequenceNumber(original.getSequenceNumber());
        replacement.setTargetDay(original.getTargetDay());
        replacement.setWindowEarlyDays(original.getWindowEarlyDays());
        replacement.setWindowLateDays(original.getWindowLateDays());
        replacement.setRequiredProcedures(original.getRequiredProcedures());
        replacement.setVisitType(original.getVisitType());
        replacement.setScheduledDate(req.newDate());
        replacement.setStatus(VisitStatus.SCHEDULED);
        replacement.setRescheduledFromVisit(original);
        replacement.setCreatedBy(original.getCreatedBy());
        replacement.setModifiedBy(original.getCreatedBy());
        replacement = visitRepository.save(replacement);

        auditService.record(
                "Visit", String.valueOf(id), AuditAction.STATE_CHANGE, VisitStatus.SCHEDULED.name(), VisitStatus.RESCHEDULED.name(), req.reasonCode());
        auditService.record(
                "Visit", String.valueOf(replacement.getId()), AuditAction.CREATE,
                null, "rescheduled from visit " + id + " to " + req.newDate(), null);
        notificationService.clearByLink(visitLink(original));

        return VisitResponse.from(replacement);
    }

    @Transactional
    public VisitResponse scheduleAdHoc(Long subjectId, CreateAdHocVisitRequest req, String actorUsername) {
        Subject subject = subjectRepository.findById(subjectId).orElseThrow(() -> new SubjectNotFoundException(subjectId));
        VisitType type = parseVisitType(req.visitType());
        User actor = currentUser(actorUsername);

        Visit visit = new Visit();
        visit.setSubject(subject);
        visit.setVisitTemplate(null);
        visit.setName(req.name());
        visit.setSequenceNumber(0);
        visit.setTargetDay((int) ChronoUnit.DAYS.between(subject.getScreeningDate(), req.scheduledDate()));
        visit.setWindowEarlyDays(0);
        visit.setWindowLateDays(0);
        visit.setRequiredProcedures(req.requiredProcedures());
        visit.setVisitType(type);
        visit.setScheduledDate(req.scheduledDate());
        visit.setStatus(VisitStatus.SCHEDULED);
        visit.setReasonCode(req.reasonCode());
        visit.setCreatedBy(actor);
        visit.setModifiedBy(actor);
        visit = visitRepository.save(visit);

        auditService.record(
                "Visit", String.valueOf(visit.getId()), AuditAction.CREATE,
                null, "ad-hoc visit \"" + visit.getName() + "\" scheduled for " + visit.getScheduledDate()
                        + " (subject " + subject.getSubjectCode() + "). Reason: " + req.reasonCode(),
                req.reasonCode());

        return VisitResponse.from(visit);
    }

    /** Ad-hoc visits (no source VisitTemplate) are excluded entirely -- compliance rate measures
     * protocol-schedule adherence, and an ad-hoc AE-follow-up visit isn't part of the protocol. */
    private double complianceRate(List<Visit> visits) {
        LocalDate today = LocalDate.now();
        long completed = 0;
        long missed = 0;
        for (Visit visit : visits) {
            if (visit.getVisitTemplate() == null || visit.getScheduledDate().isAfter(today)) {
                continue;
            }
            if (visit.getStatus() == VisitStatus.COMPLETED) {
                completed++;
            } else if (visit.getStatus() == VisitStatus.MISSED) {
                missed++;
            }
        }
        long due = completed + missed;
        return due == 0 ? 1.0 : (double) completed / due;
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private VisitType parseVisitType(String value) {
        try {
            return VisitType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidVisitTransitionException("Unknown visit type: " + value);
        }
    }

    private void notifyVisitMissed(Visit visit) {
        Subject subject = visit.getSubject();
        notificationService.notify(
                subject.getCreatedBy().getId(),
                "VISIT_MISSED",
                "Visit missed: " + visit.getName(),
                "Subject " + subject.getSubjectCode() + "'s visit \"" + visit.getName() + "\" was marked missed. Reason: " + visit.getReasonCode(),
                visitLink(visit));

        if (subject.getSite().getAssignedCra() != null) {
            notificationService.notify(
                    subject.getSite().getAssignedCra().getId(),
                    "VISIT_MISSED",
                    "Visit missed: " + visit.getName(),
                    "Subject " + subject.getSubjectCode() + " at site " + subject.getSite().getSiteCode()
                            + "'s visit \"" + visit.getName() + "\" was marked missed.",
                    visitLink(visit));
        }
    }

    private void guardScheduled(Visit visit) {
        if (visit.getStatus() != VisitStatus.SCHEDULED) {
            throw new InvalidVisitTransitionException("Cannot transition visit from " + visit.getStatus());
        }
    }

    private String visitLink(Visit visit) {
        return "/subjects/" + visit.getSubject().getId() + "/visits/" + visit.getId();
    }

    Visit findVisit(Long id) {
        return visitRepository.findById(id).orElseThrow(() -> new VisitNotFoundException(id));
    }
}
