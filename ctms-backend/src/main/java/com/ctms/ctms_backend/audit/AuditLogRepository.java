package com.ctms.ctms_backend.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityNameAndEntityId(String entityName, String entityId, Pageable pageable);

    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);

    /** Backs the Epic 11 Story 04 traceability report -- full (unpaginated) history for one
     * entity, since a traceability view is meant to show everything, not a page at a time. */
    List<AuditLog> findByEntityNameAndEntityIdOrderByPerformedAtDesc(String entityName, String entityId);
}
