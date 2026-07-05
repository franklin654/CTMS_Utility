package com.ctms.ctms_backend.milestone.repository;

import com.ctms.ctms_backend.milestone.entity.Milestone;
import com.ctms.ctms_backend.milestone.entity.MilestoneType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByStudyId(Long studyId);

    List<Milestone> findByActualDateIsNullAndPlannedDateBetween(LocalDate start, LocalDate end);

    boolean existsByStudyIdAndMilestoneType(Long studyId, MilestoneType milestoneType);
}
