package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No studyId/siteId/subjectId/screeningDate -- all locked after enrollment. */
public record UpdateSubjectRequest(
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @Size(max = 30) String gender,
        @Size(max = 50) String contactPhone,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 500) String address,
        @Size(max = 500) String emergencyContact,
        @Size(max = 2000) String notes,
        @Size(max = 4000) String medicalHistory) {}
