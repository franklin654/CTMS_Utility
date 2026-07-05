package com.ctms.ctms_backend.document.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.DocumentRepository;
import com.ctms.ctms_backend.document.dto.CreateDocumentRequirementRequest;
import com.ctms.ctms_backend.document.dto.DocumentRequirementResponse;
import com.ctms.ctms_backend.document.entity.DocumentRequirement;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.repository.DocumentRequirementRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.entity.StudyStatus;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 9 Story 03 (Document Rules Configuration). Per-study mapping of mandatory document
 * categories to Study lifecycle phases (ACTIVE="Start-Up", CONDUCT="Conduct",
 * CLOSEOUT="Closeout") -- consumed by StudyService.transition as a blocking guard, matching the
 * BRD's literal "missing mandatory documents flagged" / "blocking rules enforced before phase
 * transitions" acceptance criteria. */
@Service
public class DocumentRequirementService {

    private final DocumentRequirementRepository documentRequirementRepository;
    private final DocumentRepository documentRepository;
    private final StudyRepository studyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public DocumentRequirementService(
            DocumentRequirementRepository documentRequirementRepository,
            DocumentRepository documentRepository,
            StudyRepository studyRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.documentRequirementRepository = documentRequirementRepository;
        this.documentRepository = documentRepository;
        this.studyRepository = studyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentRequirementResponse create(CreateDocumentRequirementRequest req, String actorUsername) {
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        StudyStatus phase = StudyStatus.valueOf(req.studyPhase());
        User actor = currentUser(actorUsername);

        DocumentRequirement requirement = new DocumentRequirement();
        requirement.setStudy(study);
        requirement.setStudyPhase(phase);
        requirement.setDocumentCategory(req.documentCategory());
        requirement.setMandatory(req.mandatory());
        requirement.setCreatedBy(actor);
        requirement.setModifiedBy(actor);
        requirement = documentRequirementRepository.save(requirement);

        auditService.record(
                "DocumentRequirement", String.valueOf(requirement.getId()), AuditAction.CREATE,
                null, phase + "/" + req.documentCategory() + " (study " + study.getStudyCode() + ")", null);

        return DocumentRequirementResponse.from(requirement);
    }

    @Transactional(readOnly = true)
    public List<DocumentRequirementResponse> listByStudy(Long studyId) {
        return documentRequirementRepository.findByStudyId(studyId).stream().map(DocumentRequirementResponse::from).toList();
    }

    /** Returns the mandatory document categories for `targetPhase` that have no CURRENT document
     * of that category linked to `study` -- empty list means the transition may proceed. */
    @Transactional(readOnly = true)
    public List<String> checkRequirementsMet(Study study, StudyStatus targetPhase) {
        List<String> missing = new ArrayList<>();
        for (DocumentRequirement requirement : documentRequirementRepository.findByStudyIdAndStudyPhase(study.getId(), targetPhase)) {
            if (!requirement.isMandatory()) {
                continue;
            }
            boolean satisfied = documentRepository.existsByStudyIdAndCategoryAndCurrentVersionStatus(
                    study.getId(), requirement.getDocumentCategory(), DocumentVersionStatus.CURRENT);
            if (!satisfied) {
                missing.add(requirement.getDocumentCategory());
            }
        }
        return missing;
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
