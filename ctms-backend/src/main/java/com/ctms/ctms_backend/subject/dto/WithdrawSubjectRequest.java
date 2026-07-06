package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** BL Epic 11 Story 02 -- withdrawal is a permanent, compliance-sensitive transition, so it now
 * requires password re-authentication (like Study closeout/Document final-approval/Payment
 * release), not just a reason code. */
public record WithdrawSubjectRequest(@NotBlank @Size(max = 2000) String reasonCode, @NotBlank String password) {}
