package com.ctms.ctms_backend.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloseoutStudyRequest(@NotBlank String password, @NotBlank @Size(max = 2000) String reason) {}
