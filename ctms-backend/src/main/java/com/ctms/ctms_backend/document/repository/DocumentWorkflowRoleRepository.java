package com.ctms.ctms_backend.document.repository;

import com.ctms.ctms_backend.document.entity.DocumentWorkflowRole;
import com.ctms.ctms_backend.document.entity.ReviewStage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentWorkflowRoleRepository extends JpaRepository<DocumentWorkflowRole, Long> {

    List<DocumentWorkflowRole> findByStage(ReviewStage stage);
}
