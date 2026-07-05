package com.ctms.ctms_backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityNameAndEntityId(String entityName, String entityId, Pageable pageable);

    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);
}
