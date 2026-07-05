package com.ctms.ctms_backend.security.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
