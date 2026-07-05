package com.ctms.ctms_backend.document.repository;

import com.ctms.ctms_backend.document.entity.DocumentRequirement;
import com.ctms.ctms_backend.study.entity.StudyStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {

    List<DocumentRequirement> findByStudyId(Long studyId);

    List<DocumentRequirement> findByStudyIdAndStudyPhase(Long studyId, StudyStatus studyPhase);

    boolean existsByStudyIdAndStudyPhaseAndDocumentCategory(Long studyId, StudyStatus studyPhase, String documentCategory);
}
