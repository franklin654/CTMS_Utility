package com.ctms.ctms_backend.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** For DRAFT->ACTIVE and ACTIVE->CONDUCT only -- CONDUCT->CLOSEOUT requires {@link CloseoutStudyRequest} instead. */
public record TransitionStudyRequest(
        @NotBlank String targetStatus, @NotBlank @Size(max = 2000) String justification) {}
