package com.ctms.ctms_backend.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ReleasePaymentRequest(@NotBlank String reason, @NotBlank String password) {}
