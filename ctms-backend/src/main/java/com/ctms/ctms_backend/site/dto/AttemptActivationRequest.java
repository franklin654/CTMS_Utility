package com.ctms.ctms_backend.site.dto;

import jakarta.validation.constraints.NotBlank;

/** BL Epic 11 Story 02 -- Site activation is a go-live gate and now requires password
 * re-authentication (like Study closeout/Document final-approval/Payment release), not just an
 * unauthenticated action. */
public record AttemptActivationRequest(@NotBlank String password, @NotBlank String reason) {}
