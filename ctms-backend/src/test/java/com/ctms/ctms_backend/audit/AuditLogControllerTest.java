package com.ctms.ctms_backend.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.esignature.ESignatureResponse;
import com.ctms.ctms_backend.esignature.ESignatureService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ESignatureService eSignatureService;

    @InjectMocks
    private AuditLogController controller;

    private AuditLog logWithBeforeAfter() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setEntityName("Subject");
        log.setEntityId("1000");
        log.setAction("UPDATE");
        log.setBeforeValue("SCREENED");
        log.setAfterValue("ENROLLED");
        log.setReason("consent signed");
        return log;
    }

    @Test
    void export_includesBeforeAndAfterValueColumns() {
        Page<AuditLog> page = new PageImpl<>(List.of(logWithBeforeAfter()));
        when(auditLogRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        ResponseEntity<String> response = controller.export(null, null);

        assertTrue(response.getBody().contains("beforeValue,afterValue"));
        assertTrue(response.getBody().contains("SCREENED"));
        assertTrue(response.getBody().contains("ENROLLED"));
    }

    @Test
    void traceability_consolidatesAuditTrailAndSignatures() {
        AuditLog log = logWithBeforeAfter();
        when(auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtDesc("Subject", "1000"))
                .thenReturn(List.of(log));

        ESignatureResponse signature = new ESignatureResponse(1L, "coordinator1", "Subject", "1000", "withdrawal", Instant.now());
        when(eSignatureService.history("Subject", "1000")).thenReturn(List.of(signature));

        TraceabilityResponse response = controller.traceability("Subject", "1000");

        assertEquals(1, response.auditTrail().size());
        assertEquals(1, response.signatures().size());
        assertEquals("Subject", response.entityName());
        assertEquals("1000", response.entityId());
    }
}
