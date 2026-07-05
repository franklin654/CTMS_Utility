package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Forward transitions only (SCREENED->ENROLLED->IN_TREATMENT->COMPLETED) -- withdrawal has its
 * own dedicated endpoint/DTO, mirrors Study's transition-vs-closeout split. */
public record TransitionSubjectRequest(
        @NotBlank String targetStatus, @NotBlank @Size(max = 2000) String justification) {}
