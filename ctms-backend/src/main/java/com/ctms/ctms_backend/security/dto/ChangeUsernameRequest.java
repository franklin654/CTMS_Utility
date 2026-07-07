package com.ctms.ctms_backend.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 3, max = 100)
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "Username may only contain letters, numbers, dots, underscores, and hyphens")
        String newUsername) {}
