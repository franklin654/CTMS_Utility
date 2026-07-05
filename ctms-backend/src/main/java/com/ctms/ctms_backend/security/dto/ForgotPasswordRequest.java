package com.ctms.ctms_backend.security.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank String email) {}
