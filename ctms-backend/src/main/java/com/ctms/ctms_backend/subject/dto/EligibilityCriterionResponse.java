package com.ctms.ctms_backend.subject.dto;

import com.ctms.ctms_backend.subject.entity.EligibilityCriterion;

public record EligibilityCriterionResponse(Long id, Long studyId, String label, String criterionType, boolean active) {

    public static EligibilityCriterionResponse from(EligibilityCriterion c) {
        return new EligibilityCriterionResponse(
                c.getId(), c.getStudy().getId(), c.getLabel(), c.getCriterionType().name(), c.isActive());
    }
}
