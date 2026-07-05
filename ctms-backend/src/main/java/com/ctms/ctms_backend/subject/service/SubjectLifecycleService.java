package com.ctms.ctms_backend.subject.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.SubjectStatusHistoryResponse;
import com.ctms.ctms_backend.subject.dto.TransitionSubjectRequest;
import com.ctms.ctms_backend.subject.dto.WithdrawSubjectRequest;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectStatus;
import com.ctms.ctms_backend.subject.entity.SubjectStatusHistory;
import com.ctms.ctms_backend.subject.exception.InvalidSubjectTransitionException;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.subject.repository.SubjectStatusHistoryRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subject lifecycle state machine (Epic 3 Story 02): linear forward progression
 * SCREENED -> ENROLLED -> IN_TREATMENT -> COMPLETED via {@link #transition}, with WITHDRAWN
 * reachable from any non-terminal state via the dedicated {@link #withdraw} action -- mirrors
 * Study's transition-vs-closeout split (StudyService). */
@Service
public class SubjectLifecycleService {

    private static final Map<SubjectStatus, SubjectStatus> NEXT_STATUS = Map.of(
            SubjectStatus.SCREENED, SubjectStatus.ENROLLED,
            SubjectStatus.ENROLLED, SubjectStatus.IN_TREATMENT,
            SubjectStatus.IN_TREATMENT, SubjectStatus.COMPLETED);

    private static final Set<SubjectStatus> WITHDRAWABLE_FROM =
            Set.of(SubjectStatus.SCREENED, SubjectStatus.ENROLLED, SubjectStatus.IN_TREATMENT);

    private final SubjectRepository subjectRepository;
    private final SubjectStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public SubjectLifecycleService(
            SubjectRepository subjectRepository,
            SubjectStatusHistoryRepository historyRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.subjectRepository = subjectRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SubjectResponse transition(Long id, TransitionSubjectRequest req, String actorUsername) {
        Subject subject = findSubject(id);
        SubjectStatus targetStatus = parseStatus(req.targetStatus());

        if (targetStatus == SubjectStatus.WITHDRAWN) {
            throw new InvalidSubjectTransitionException(
                    "WITHDRAWN requires a reason code -- use POST /api/subjects/{id}/withdraw instead");
        }

        SubjectStatus current = subject.getStatus();
        SubjectStatus expectedNext = NEXT_STATUS.get(current);
        if (expectedNext == null || expectedNext != targetStatus) {
            throw new InvalidSubjectTransitionException("Cannot transition subject from " + current + " to " + targetStatus);
        }

        User actor = currentUser(actorUsername);
        recordTransition(subject, current, targetStatus, req.justification(), actor);
        return SubjectResponse.from(subject, currentRoleCodes());
    }

    @Transactional
    public SubjectResponse withdraw(Long id, WithdrawSubjectRequest req, String actorUsername) {
        Subject subject = findSubject(id);
        SubjectStatus current = subject.getStatus();
        if (!WITHDRAWABLE_FROM.contains(current)) {
            throw new InvalidSubjectTransitionException("Cannot withdraw subject from status " + current);
        }

        User actor = currentUser(actorUsername);
        recordTransition(subject, current, SubjectStatus.WITHDRAWN, req.reasonCode(), actor);
        return SubjectResponse.from(subject, currentRoleCodes());
    }

    @Transactional(readOnly = true)
    public List<SubjectStatusHistoryResponse> history(Long id) {
        if (!subjectRepository.existsById(id)) {
            throw new SubjectNotFoundException(id);
        }
        return historyRepository.findBySubjectIdOrderByChangedAtDesc(id).stream()
                .map(SubjectStatusHistoryResponse::from)
                .toList();
    }

    private void recordTransition(Subject subject, SubjectStatus from, SubjectStatus to, String reasonOrJustification, User actor) {
        SubjectStatusHistory history = new SubjectStatusHistory();
        history.setSubject(subject);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReasonCode(reasonOrJustification);
        history.setChangedBy(actor);
        historyRepository.save(history);

        subject.setStatus(to);
        subject.setModifiedBy(actor);
        subjectRepository.save(subject);

        auditService.record("Subject", String.valueOf(subject.getId()), AuditAction.STATE_CHANGE, from.name(), to.name(), reasonOrJustification);
        notifySiteCra(subject, from, to, reasonOrJustification);
    }

    private void notifySiteCra(Subject subject, SubjectStatus from, SubjectStatus to, String reasonOrJustification) {
        notificationService.notify(
                subject.getCreatedBy().getId(),
                "SUBJECT_STATE_CHANGE",
                "Subject " + subject.getSubjectCode() + " moved to " + to,
                "Subject " + subject.getSubjectCode() + " transitioned " + from + " -> " + to + ". " + reasonOrJustification,
                "/subjects/" + subject.getId());

        if (subject.getSite().getAssignedCra() != null) {
            notificationService.notify(
                    subject.getSite().getAssignedCra().getId(),
                    "SUBJECT_STATE_CHANGE",
                    "Subject " + subject.getSubjectCode() + " moved to " + to,
                    "Subject " + subject.getSubjectCode() + " at site " + subject.getSite().getSiteCode()
                            + " transitioned " + from + " -> " + to + ".",
                    "/subjects/" + subject.getId());
        }
    }

    private Subject findSubject(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new SubjectNotFoundException(id));
    }

    private SubjectStatus parseStatus(String value) {
        try {
            return SubjectStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidSubjectTransitionException("Unknown status: " + value);
        }
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private Set<String> currentRoleCodes() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(java.util.stream.Collectors.toSet());
    }
}
