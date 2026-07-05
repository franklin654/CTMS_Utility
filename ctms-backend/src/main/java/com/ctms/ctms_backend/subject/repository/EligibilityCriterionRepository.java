package com.ctms.ctms_backend.subject.repository;

import com.ctms.ctms_backend.subject.entity.EligibilityCriterion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EligibilityCriterionRepository extends JpaRepository<EligibilityCriterion, Long> {

    List<EligibilityCriterion> findByStudyIdAndActiveTrue(Long studyId);
}
