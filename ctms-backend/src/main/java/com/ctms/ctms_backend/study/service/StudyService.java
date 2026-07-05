package com.ctms.ctms_backend.study.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.dto.CloseoutStudyRequest;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.dto.StudyStatusHistoryResponse;
import com.ctms.ctms_backend.study.dto.TransitionStudyRequest;
import com.ctms.ctms_backend.study.dto.UpdateStudyRequest;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.entity.StudyStatus;
import com.ctms.ctms_backend.study.entity.StudyStatusHistory;
import com.ctms.ctms_backend.study.exception.DuplicateProtocolIdException;
import com.ctms.ctms_backend.study.exception.InvalidStudyTransitionException;
import com.ctms.ctms_backend.study.exception.StudyClosedException;
import com.ctms.ctms_backend.study.exception.StudyFieldLockedException;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.study.repository.StudyStatusHistoryRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyService {

    private static final Map<StudyStatus, StudyStatus> NEXT_STATUS = Map.of(
            StudyStatus.DRAFT, StudyStatus.ACTIVE,
            StudyStatus.ACTIVE, StudyStatus.CONDUCT,
            StudyStatus.CONDUCT, StudyStatus.CLOSEOUT);

    private final StudyRepository studyRepository;
    private final StudyStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ESignatureService eSignatureService;

    public StudyService(
            StudyRepository studyRepository,
            StudyStatusHistoryRepository historyRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService,
            ESignatureService eSignatureService) {
        this.studyRepository = studyRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.eSignatureService = eSignatureService;
    }

    @Transactional
    public StudyResponse createStudy(CreateStudyRequest req, String creatorUsername) {
        if (studyRepository.existsByProtocolId(req.protocolId())) {
            throw new DuplicateProtocolIdException(req.protocolId());
        }
        User creator = currentUser(creatorUsername);

        Study study = new Study();
        study.setName(req.name());
        study.setProtocolId(req.protocolId());
        study.setProtocolVersion(req.protocolVersion());
        study.setPhase(req.phase());
        study.setSponsor(req.sponsor());
        study.setPlannedStartDate(req.plannedStartDate());
        study.setPlannedEndDate(req.plannedEndDate());
        study.setDescription(req.description());
        study.setStatus(StudyStatus.DRAFT);
        study.setCreatedBy(creator);
        study.setModifiedBy(creator);
        study = studyRepository.save(study);

        study.setStudyCode(String.format("ST-%06d", study.getId()));
        study = studyRepository.save(study);

        auditService.record(
                "Study",
                String.valueOf(study.getId()),
                AuditAction.CREATE,
                null,
                "created study " + study.getStudyCode() + " protocol " + study.getProtocolId(),
                null);

        return StudyResponse.from(study);
    }

    @Transactional
    public StudyResponse updateStudy(Long id, UpdateStudyRequest req, String updaterUsername) {
        Study study = studyRepository.findById(id).orElseThrow(() -> new StudyNotFoundException(id));

        if (study.getStatus() == StudyStatus.CLOSEOUT) {
            throw new StudyClosedException(id);
        }
        if (study.getStatus() != StudyStatus.DRAFT && !req.protocolId().equals(study.getProtocolId())) {
            throw new StudyFieldLockedException("protocolId", study.getStatus());
        }
        if (study.getStatus() == StudyStatus.DRAFT
                && !req.protocolId().equals(study.getProtocolId())
                && studyRepository.existsByProtocolId(req.protocolId())) {
            throw new DuplicateProtocolIdException(req.protocolId());
        }

        String beforeSnapshot = snapshot(study);
        User updater = currentUser(updaterUsername);

        study.setName(req.name());
        if (study.getStatus() == StudyStatus.DRAFT) {
            study.setProtocolId(req.protocolId());
        }
        study.setProtocolVersion(req.protocolVersion());
        study.setPhase(req.phase());
        study.setSponsor(req.sponsor());
        study.setPlannedStartDate(req.plannedStartDate());
        study.setPlannedEndDate(req.plannedEndDate());
        study.setActualStartDate(req.actualStartDate());
        study.setActualEndDate(req.actualEndDate());
        study.setDescription(req.description());
        study.setModifiedBy(updater);
        study = studyRepository.save(study);

        auditService.record("Study", String.valueOf(id), AuditAction.UPDATE, beforeSnapshot, snapshot(study), null);
        return StudyResponse.from(study);
    }

    @Transactional
    public StudyResponse transition(Long id, TransitionStudyRequest req, String actorUsername) {
        Study study = studyRepository.findById(id).orElseThrow(() -> new StudyNotFoundException(id));
        StudyStatus targetStatus = parseStatus(req.targetStatus());

        if (targetStatus == StudyStatus.CLOSEOUT) {
            throw new InvalidStudyTransitionException(
                    "CLOSEOUT requires password re-authentication -- use POST /api/studies/{id}/closeout instead");
        }

        StudyStatus current = study.getStatus();
        StudyStatus expectedNext = NEXT_STATUS.get(current);
        if (expectedNext == null || expectedNext != targetStatus) {
            throw new InvalidStudyTransitionException("Cannot transition study from " + current + " to " + targetStatus);
        }

        User actor = currentUser(actorUsername);

        StudyStatusHistory history = new StudyStatusHistory();
        history.setStudy(study);
        history.setFromStatus(current);
        history.setToStatus(targetStatus);
        history.setJustification(req.justification());
        history.setChangedBy(actor);
        historyRepository.save(history);

        study.setStatus(targetStatus);
        study.setModifiedBy(actor);
        study = studyRepository.save(study);

        auditService.record("Study", String.valueOf(id), AuditAction.STATE_CHANGE, current.name(), targetStatus.name(), req.justification());
        notifyOwner(study, current, targetStatus, req.justification());

        return StudyResponse.from(study);
    }

    @Transactional
    public StudyResponse closeout(Long id, CloseoutStudyRequest req, String actorUsername) {
        Study study = studyRepository.findById(id).orElseThrow(() -> new StudyNotFoundException(id));

        if (study.getStatus() != StudyStatus.CONDUCT) {
            throw new InvalidStudyTransitionException(
                    "Cannot close out study from status " + study.getStatus() + " (must be CONDUCT)");
        }

        ESignature signature = eSignatureService.sign(actorUsername, req.password(), "Study", String.valueOf(id), req.reason());
        User actor = currentUser(actorUsername);

        StudyStatusHistory history = new StudyStatusHistory();
        history.setStudy(study);
        history.setFromStatus(StudyStatus.CONDUCT);
        history.setToStatus(StudyStatus.CLOSEOUT);
        history.setJustification(req.reason());
        history.setChangedBy(actor);
        history.setEsignature(signature);
        historyRepository.save(history);

        study.setStatus(StudyStatus.CLOSEOUT);
        study.setModifiedBy(actor);
        study = studyRepository.save(study);

        auditService.record(
                "Study", String.valueOf(id), AuditAction.STATE_CHANGE, StudyStatus.CONDUCT.name(), StudyStatus.CLOSEOUT.name(), req.reason());
        notifyOwner(study, StudyStatus.CONDUCT, StudyStatus.CLOSEOUT, req.reason());

        return StudyResponse.from(study);
    }

    @Transactional(readOnly = true)
    public StudyResponse get(Long id) {
        return StudyResponse.from(studyRepository.findById(id).orElseThrow(() -> new StudyNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public Page<StudyResponse> list(String search, Pageable pageable) {
        Page<Study> page = (search == null || search.isBlank())
                ? studyRepository.findAll(pageable)
                : studyRepository.findByNameContainingIgnoreCaseOrProtocolIdContainingIgnoreCase(search, search, pageable);
        return page.map(StudyResponse::from);
    }

    @Transactional(readOnly = true)
    public List<StudyStatusHistoryResponse> history(Long id) {
        if (!studyRepository.existsById(id)) {
            throw new StudyNotFoundException(id);
        }
        return historyRepository.findByStudyIdOrderByChangedAtDesc(id).stream()
                .map(StudyStatusHistoryResponse::from)
                .toList();
    }

    private void notifyOwner(Study study, StudyStatus from, StudyStatus to, String justification) {
        User owner = study.getCreatedBy();
        notificationService.notify(
                owner.getId(),
                "STUDY_STATE_CHANGE",
                "Study " + study.getStudyCode() + " moved to " + to,
                "Study " + study.getName() + " (" + study.getProtocolId() + ") transitioned " + from + " -> " + to
                        + ". Justification: " + justification,
                "/studies/" + study.getId());
    }

    private StudyStatus parseStatus(String value) {
        try {
            return StudyStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidStudyTransitionException("Unknown status: " + value);
        }
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private String snapshot(Study s) {
        return s.getName() + "|" + s.getProtocolId() + "|" + s.getProtocolVersion() + "|" + s.getPhase()
                + "|" + s.getSponsor() + "|" + s.getPlannedStartDate() + "|" + s.getPlannedEndDate()
                + "|" + s.getActualStartDate() + "|" + s.getActualEndDate();
    }
}
