package com.ctms.ctms_backend.audit;

import com.ctms.ctms_backend.esignature.ESignatureService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** Read-only audit trail access -- write path is {@link AuditService}, never this controller. */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN', 'QA_COMPLIANCE_AUDITOR')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final ESignatureService eSignatureService;

    public AuditLogController(AuditLogRepository auditLogRepository, ESignatureService eSignatureService) {
        this.auditLogRepository = auditLogRepository;
        this.eSignatureService = eSignatureService;
    }

    /** BL Epic 11 Story 04 -- consolidated per-entity view joining the full audit trail with any
     * e-signatures captured against it, reusing ESignatureService.history unchanged. */
    @GetMapping("/traceability/{entityName}/{entityId}")
    @Transactional(readOnly = true)
    public TraceabilityResponse traceability(@PathVariable String entityName, @PathVariable String entityId) {
        var auditTrail = auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtDesc(entityName, entityId).stream()
                .map(AuditLogResponse::from)
                .toList();
        var signatures = eSignatureService.history(entityName, entityId);
        return new TraceabilityResponse(entityName, entityId, auditTrail, signatures);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String entityId,
            @PageableDefault(size = 50, sort = "performedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> page = resolvePage(entityName, entityId, pageable);
        return page.map(AuditLogResponse::from);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> export(
            @RequestParam(required = false) String entityName, @RequestParam(required = false) String entityId) {
        Page<AuditLog> page = resolvePage(entityName, entityId, Pageable.ofSize(10_000));
        StringBuilder csv = new StringBuilder("id,entityName,entityId,action,performedBy,performedAt,beforeValue,afterValue,reason\n");
        for (AuditLog log : page.getContent()) {
            csv.append(csvRow(log));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    private Page<AuditLog> resolvePage(String entityName, String entityId, Pageable pageable) {
        if (entityName != null && entityId != null) {
            return auditLogRepository.findByEntityNameAndEntityId(entityName, entityId, pageable);
        }
        if (entityName != null) {
            return auditLogRepository.findByEntityName(entityName, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    private String csvRow(AuditLog log) {
        return String.join(
                        ",",
                        String.valueOf(log.getId()),
                        escape(log.getEntityName()),
                        escape(log.getEntityId()),
                        escape(log.getAction()),
                        escape(log.getPerformedBy() == null ? "" : log.getPerformedBy().getUsername()),
                        String.valueOf(log.getPerformedAt()),
                        escape(log.getBeforeValue()),
                        escape(log.getAfterValue()),
                        escape(log.getReason()))
                + "\n";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\n") ? "\"" + escaped + "\"" : escaped;
    }
}
