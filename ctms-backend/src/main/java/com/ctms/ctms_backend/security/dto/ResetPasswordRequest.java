package com.ctms.ctms_backend.security.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}
