package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record EnrollSubjectRequest(
        @NotNull Long studyId,
        @NotNull Long siteId,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotNull LocalDate dateOfBirth,
        @Size(max = 30) String gender,
        @Size(max = 50) String contactPhone,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 500) String address,
        @Size(max = 500) String emergencyContact,
        @Size(max = 2000) String notes,
        @Size(max = 4000) String medicalHistory,
        @NotNull LocalDate screeningDate,
        /** Empty is valid -- a study with zero configured EligibilityCriterion rows has nothing
         * to answer. SubjectService.enrollSubject's own loop only requires answers for criteria
         * that actually exist, so an empty list here is not a client error. */
        @NotNull List<@Valid EligibilityAnswerRequest> eligibilityAnswers) {}
