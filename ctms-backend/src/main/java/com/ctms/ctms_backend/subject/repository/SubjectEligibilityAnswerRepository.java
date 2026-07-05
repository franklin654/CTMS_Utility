package com.ctms.ctms_backend.subject.repository;

import com.ctms.ctms_backend.subject.entity.SubjectEligibilityAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectEligibilityAnswerRepository extends JpaRepository<SubjectEligibilityAnswer, Long> {

    List<SubjectEligibilityAnswer> findBySubjectId(Long subjectId);
}
