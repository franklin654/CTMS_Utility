package com.ctms.ctms_backend.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateChecklistItemRequest(
        @NotBlank String status, LocalDate completedDate, @Size(max = 1000) String note) {}
