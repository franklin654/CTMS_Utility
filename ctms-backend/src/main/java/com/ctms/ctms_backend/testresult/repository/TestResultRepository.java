package com.ctms.ctms_backend.testresult.repository;

import com.ctms.ctms_backend.testresult.entity.TestResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findBySubjectIdOrderByCreatedAtDesc(Long subjectId);

    List<TestResult> findByVisitId(Long visitId);
}
