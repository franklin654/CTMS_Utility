package com.ctms.ctms_backend.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank String currentPassword,
        @NotBlank @Email @Size(max = 255) String newEmail) {}
