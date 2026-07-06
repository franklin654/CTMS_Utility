package com.ctms.ctms_backend.adverseevent.dto;

import jakarta.validation.constraints.NotBlank;

/** BL Epic 11 Story 02 -- resolving an AE closes a safety event and now requires password
 * re-authentication (like Study closeout/Document final-approval/Payment release), not just
 * resolution notes. */
public record ResolveAdverseEventRequest(@NotBlank String resolutionNotes, @NotBlank String password) {}
