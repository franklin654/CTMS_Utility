package com.ctms.ctms_backend.visit.repository;

import com.ctms.ctms_backend.visit.entity.VisitTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitTemplateRepository extends JpaRepository<VisitTemplate, Long> {

    List<VisitTemplate> findByStudyIdAndActiveTrueOrderBySequenceNumber(Long studyId);
}
