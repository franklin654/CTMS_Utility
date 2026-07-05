package com.ctms.ctms_backend.milestone.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.milestone.dto.CreateMilestoneRequest;
import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import com.ctms.ctms_backend.milestone.dto.RecordMilestoneActualRequest;
import com.ctms.ctms_backend.milestone.dto.UpdateMilestonePlannedDateRequest;
import com.ctms.ctms_backend.milestone.entity.Milestone;
import com.ctms.ctms_backend.milestone.entity.MilestoneType;
import com.ctms.ctms_backend.milestone.exception.DuplicateMilestoneTypeException;
import com.ctms.ctms_backend.milestone.exception.InvalidMilestoneActualDateException;
import com.ctms.ctms_backend.milestone.exception.MilestoneNotFoundException;
import com.ctms.ctms_backend.milestone.repository.MilestoneRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 6 Story 04. One row per (study, milestoneType) -- enforced proactively (existence
 * check before insert, mirrors DuplicateSiteCodeException's pattern) plus the DB unique
 * constraint as a backstop. actualDate is only ever set via the dedicated recordActual action. */
@Service
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final StudyRepository studyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public MilestoneService(
            MilestoneRepository milestoneRepository,
            StudyRepository studyRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.milestoneRepository = milestoneRepository;
        this.studyRepository = studyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MilestoneResponse create(CreateMilestoneRequest req, String actorUsername) {
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        MilestoneType type = MilestoneType.valueOf(req.milestoneType());
        if (milestoneRepository.existsByStudyIdAndMilestoneType(req.studyId(), type)) {
            throw new DuplicateMilestoneTypeException(req.studyId(), req.milestoneType());
        }
        User actor = currentUser(actorUsername);

        Milestone milestone = new Milestone();
        milestone.setStudy(study);
        milestone.setMilestoneType(type);
        milestone.setPlannedDate(req.plannedDate());
        milestone.setCreatedBy(actor);
        milestone.setModifiedBy(actor);
        milestone = milestoneRepository.save(milestone);

        auditService.record(
                "Milestone", String.valueOf(milestone.getId()), AuditAction.CREATE,
                null, type + " planned for " + req.plannedDate() + " (study " + study.getStudyCode() + ")", null);

        return MilestoneResponse.from(milestone);
    }

    @Transactional
    public MilestoneResponse updatePlannedDate(Long id, UpdateMilestonePlannedDateRequest req, String actorUsername) {
        Milestone milestone = findMilestone(id);
        if (milestone.getActualDate() != null) {
            throw new InvalidMilestoneActualDateException("Cannot change the planned date of a milestone that has already been reached");
        }
        User actor = currentUser(actorUsername);

        String before = milestone.getPlannedDate().toString();
        milestone.setPlannedDate(req.plannedDate());
        milestone.setModifiedBy(actor);
        milestone = milestoneRepository.save(milestone);

        auditService.record("Milestone", String.valueOf(id), AuditAction.UPDATE, before, req.plannedDate().toString(), null);
        return MilestoneResponse.from(milestone);
    }

    @Transactional
    public MilestoneResponse recordActual(Long id, RecordMilestoneActualRequest req, String actorUsername) {
        Milestone milestone = findMilestone(id);
        if (milestone.getActualDate() != null) {
            throw new InvalidMilestoneActualDateException("Milestone " + id + " already has an actual date recorded");
        }
        User actor = currentUser(actorUsername);

        milestone.setActualDate(req.actualDate());
        milestone.setModifiedBy(actor);
        milestone = milestoneRepository.save(milestone);

        auditService.record("Milestone", String.valueOf(id), AuditAction.STATE_CHANGE, null, "actual date recorded: " + req.actualDate(), null);
        return MilestoneResponse.from(milestone);
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> listByStudy(Long studyId) {
        return milestoneRepository.findByStudyId(studyId).stream().map(MilestoneResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MilestoneResponse get(Long id) {
        return MilestoneResponse.from(findMilestone(id));
    }

    private Milestone findMilestone(Long id) {
        return milestoneRepository.findById(id).orElseThrow(() -> new MilestoneNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
