package com.ctms.ctms_backend.subject.repository;

import com.ctms.ctms_backend.subject.entity.SubjectStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectStatusHistoryRepository extends JpaRepository<SubjectStatusHistory, Long> {

    List<SubjectStatusHistory> findBySubjectIdOrderByChangedAtDesc(Long subjectId);
}
