package com.ctms.ctms_backend.patientportal.dto;

import jakarta.validation.constraints.NotBlank;

/** Thin patient-facing wrapper over Phase 7's ReportAdverseEventRequest -- deliberately omits
 * subjectId/visitId, both of which are always resolved server-side (via PatientContextService),
 * never trusted from the client. */
public record PatientReportAdverseEventRequest(@NotBlank String description, @NotBlank String severity) {}
