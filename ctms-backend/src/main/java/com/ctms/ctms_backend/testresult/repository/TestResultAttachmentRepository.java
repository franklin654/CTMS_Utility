package com.ctms.ctms_backend.testresult.repository;

import com.ctms.ctms_backend.testresult.entity.TestResultAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultAttachmentRepository extends JpaRepository<TestResultAttachment, Long> {

    List<TestResultAttachment> findByTestResultId(Long testResultId);
}
