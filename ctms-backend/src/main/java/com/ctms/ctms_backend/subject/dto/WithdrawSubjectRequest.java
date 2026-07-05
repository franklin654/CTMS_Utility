package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawSubjectRequest(@NotBlank @Size(max = 2000) String reasonCode) {}
