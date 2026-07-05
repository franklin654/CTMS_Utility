package com.ctms.ctms_backend.study.repository;

import com.ctms.ctms_backend.study.entity.StudyStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyStatusHistoryRepository extends JpaRepository<StudyStatusHistory, Long> {

    List<StudyStatusHistory> findByStudyIdOrderByChangedAtDesc(Long studyId);
}
