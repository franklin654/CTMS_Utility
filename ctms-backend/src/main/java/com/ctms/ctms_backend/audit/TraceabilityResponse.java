package com.ctms.ctms_backend.audit;

import com.ctms.ctms_backend.esignature.ESignatureResponse;
import java.util.List;

/** BL Epic 11 Story 04 (End-to-End Traceability). Consolidates a single entity's full audit
 * trail with any e-signatures captured against it -- extends the existing AuditLogController
 * (list/CSV export) rather than duplicating it, per the research's own finding that this
 * infrastructure already substantially pre-empts the story. */
public record TraceabilityResponse(String entityName, String entityId, List<AuditLogResponse> auditTrail, List<ESignatureResponse> signatures) {}
