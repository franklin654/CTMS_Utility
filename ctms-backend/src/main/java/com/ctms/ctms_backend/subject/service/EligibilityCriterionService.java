package com.ctms.ctms_backend.subject.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.dto.CreateEligibilityCriterionRequest;
import com.ctms.ctms_backend.subject.dto.EligibilityCriterionResponse;
import com.ctms.ctms_backend.subject.entity.CriterionType;
import com.ctms.ctms_backend.subject.entity.EligibilityCriterion;
import com.ctms.ctms_backend.subject.exception.EligibilityCriterionNotFoundException;
import com.ctms.ctms_backend.subject.exception.InvalidSubjectTransitionException;
import com.ctms.ctms_backend.subject.repository.EligibilityCriterionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EligibilityCriterionService {

    private final EligibilityCriterionRepository criterionRepository;
    private final StudyRepository studyRepository;
    private final AuditService auditService;

    public EligibilityCriterionService(
            EligibilityCriterionRepository criterionRepository,
            StudyRepository studyRepository,
            AuditService auditService) {
        this.criterionRepository = criterionRepository;
        this.studyRepository = studyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public EligibilityCriterionResponse create(CreateEligibilityCriterionRequest req) {
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        CriterionType type = parseType(req.criterionType());

        EligibilityCriterion criterion = new EligibilityCriterion();
        criterion.setStudy(study);
        criterion.setLabel(req.label());
        criterion.setCriterionType(type);
        criterion.setActive(true);
        criterion = criterionRepository.save(criterion);

        auditService.record(
                "EligibilityCriterion", String.valueOf(criterion.getId()), AuditAction.CREATE,
                null, type + ": " + req.label() + " (study " + study.getStudyCode() + ")", null);

        return EligibilityCriterionResponse.from(criterion);
    }

    @Transactional(readOnly = true)
    public List<EligibilityCriterionResponse> listActive(Long studyId) {
        return criterionRepository.findByStudyIdAndActiveTrue(studyId).stream()
                .map(EligibilityCriterionResponse::from)
                .toList();
    }

    @Transactional
    public EligibilityCriterionResponse deactivate(Long id) {
        EligibilityCriterion criterion = criterionRepository.findById(id)
                .orElseThrow(() -> new EligibilityCriterionNotFoundException(id));
        criterion.setActive(false);
        criterion = criterionRepository.save(criterion);

        auditService.record(
                "EligibilityCriterion", String.valueOf(id), AuditAction.UPDATE, "active", "inactive", null);

        return EligibilityCriterionResponse.from(criterion);
    }

    private CriterionType parseType(String value) {
        try {
            return CriterionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidSubjectTransitionException("Unknown criterion type: " + value);
        }
    }
}
