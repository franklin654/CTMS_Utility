package com.ctms.ctms_backend.adverseevent.service;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.adverseevent.dto.ReportAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.ResolveAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.TransitionAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEvent;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventSeverity;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventStatus;
import com.ctms.ctms_backend.adverseevent.exception.AdverseEventNotFoundException;
import com.ctms.ctms_backend.adverseevent.exception.InvalidAdverseEventTransitionException;
import com.ctms.ctms_backend.adverseevent.repository.AdverseEventRepository;
import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.exception.VisitNotFoundException;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL-08 (not part of backlog v0.2, reintroduced per Phase 7 scope decision). Status workflow
 * OPEN -> UNDER_REVIEW -> RESOLVED: UNDER_REVIEW is a generic transition, RESOLVED is only ever
 * reached via the dedicated {@link #resolve} action requiring resolutionNotes -- mirrors Subject
 * withdrawal / Study closeout's "dedicated action for a terminal/compliance-sensitive transition"
 * pattern. SEVERE/LIFE_THREATENING severities auto-create an escalation Task by reusing Phase 6's
 * engine exactly (TaskRuleService supplies SLA/priority for the ADVERSE_EVENT_HIGH_SEVERITY event
 * code; this service resolves the actual owner/escalation-target User, same as VISIT_MISSED). */
@Service
public class AdverseEventService {

    private static final Set<AdverseEventSeverity> ESCALATING_SEVERITIES =
            Set.of(AdverseEventSeverity.SEVERE, AdverseEventSeverity.LIFE_THREATENING);

    private final AdverseEventRepository adverseEventRepository;
    private final SubjectRepository subjectRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TaskService taskService;

    public AdverseEventService(
            AdverseEventRepository adverseEventRepository,
            SubjectRepository subjectRepository,
            VisitRepository visitRepository,
            UserRepository userRepository,
            AuditService auditService,
            TaskService taskService) {
        this.adverseEventRepository = adverseEventRepository;
        this.subjectRepository = subjectRepository;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.taskService = taskService;
    }

    @Transactional
    public AdverseEventResponse report(ReportAdverseEventRequest req, String actorUsername) {
        Subject subject = subjectRepository.findById(req.subjectId())
                .orElseThrow(() -> new SubjectNotFoundException(req.subjectId()));
        Visit visit = req.visitId() != null
                ? visitRepository.findById(req.visitId()).orElseThrow(() -> new VisitNotFoundException(req.visitId()))
                : null;
        AdverseEventSeverity severity = parseSeverity(req.severity());
        User actor = currentUser(actorUsername);

        AdverseEvent ae = new AdverseEvent();
        ae.setSubject(subject);
        ae.setVisit(visit);
        ae.setDescription(req.description());
        ae.setSeverity(severity);
        ae.setStatus(AdverseEventStatus.OPEN);
        ae.setCreatedBy(actor);
        ae.setModifiedBy(actor);
        ae = adverseEventRepository.save(ae);

        auditService.record(
                "AdverseEvent", String.valueOf(ae.getId()), AuditAction.CREATE,
                null, severity + ": " + req.description() + " (subject " + subject.getSubjectCode() + ")", null);

        if (ESCALATING_SEVERITIES.contains(severity)) {
            createEscalationTask(ae, actorUsername);
        }

        return AdverseEventResponse.from(ae);
    }

    @Transactional
    public AdverseEventResponse transition(Long id, TransitionAdverseEventRequest req, String actorUsername) {
        AdverseEvent ae = findAdverseEvent(id);
        AdverseEventStatus target = parseStatus(req.targetStatus());
        if (ae.getStatus() != AdverseEventStatus.OPEN || target != AdverseEventStatus.UNDER_REVIEW) {
            throw new InvalidAdverseEventTransitionException(
                    "Cannot transition adverse event from " + ae.getStatus() + " to " + target);
        }
        User actor = currentUser(actorUsername);

        ae.setStatus(AdverseEventStatus.UNDER_REVIEW);
        ae.setModifiedBy(actor);
        ae = adverseEventRepository.save(ae);

        auditService.record(
                "AdverseEvent", String.valueOf(id), AuditAction.STATE_CHANGE,
                AdverseEventStatus.OPEN.name(), AdverseEventStatus.UNDER_REVIEW.name(), req.justification());

        return AdverseEventResponse.from(ae);
    }

    @Transactional
    public AdverseEventResponse resolve(Long id, ResolveAdverseEventRequest req, String actorUsername) {
        AdverseEvent ae = findAdverseEvent(id);
        if (ae.getStatus() != AdverseEventStatus.UNDER_REVIEW) {
            throw new InvalidAdverseEventTransitionException("Cannot resolve adverse event from status " + ae.getStatus());
        }
        User actor = currentUser(actorUsername);

        ae.setStatus(AdverseEventStatus.RESOLVED);
        ae.setResolutionNotes(req.resolutionNotes());
        ae.setResolvedAt(Instant.now());
        ae.setModifiedBy(actor);
        ae = adverseEventRepository.save(ae);

        auditService.record(
                "AdverseEvent", String.valueOf(id), AuditAction.STATE_CHANGE,
                AdverseEventStatus.UNDER_REVIEW.name(), AdverseEventStatus.RESOLVED.name(), req.resolutionNotes());

        return AdverseEventResponse.from(ae);
    }

    @Transactional(readOnly = true)
    public List<AdverseEventResponse> list(Long subjectId) {
        return adverseEventRepository.findBySubjectIdOrderByCreatedAtDesc(subjectId).stream()
                .map(AdverseEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdverseEventResponse> board() {
        return adverseEventRepository.findAll().stream().map(AdverseEventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AdverseEventResponse get(Long id) {
        return AdverseEventResponse.from(findAdverseEvent(id));
    }

    private void createEscalationTask(AdverseEvent ae, String actorUsername) {
        Subject subject = ae.getSubject();
        User owner = subject.getSite().getAssignedCra() != null ? subject.getSite().getAssignedCra() : subject.getCreatedBy();
        User escalationTarget = subject.getStudy().getCreatedBy();
        taskService.createTask(
                "ADVERSE_EVENT_HIGH_SEVERITY",
                "Review " + ae.getSeverity() + " adverse event: " + subject.getSubjectCode(),
                ae.getDescription(),
                "AdverseEvent", ae.getId(), owner.getId(), escalationTarget.getId(), actorUsername);
    }

    AdverseEvent findAdverseEvent(Long id) {
        return adverseEventRepository.findById(id).orElseThrow(() -> new AdverseEventNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private AdverseEventSeverity parseSeverity(String value) {
        try {
            return AdverseEventSeverity.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidAdverseEventTransitionException("Unknown severity: " + value);
        }
    }

    private AdverseEventStatus parseStatus(String value) {
        try {
            return AdverseEventStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidAdverseEventTransitionException("Unknown status: " + value);
        }
    }
}
