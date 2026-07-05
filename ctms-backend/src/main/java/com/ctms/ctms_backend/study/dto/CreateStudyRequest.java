package com.ctms.ctms_backend.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateStudyRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String protocolId,
        @NotBlank @Size(max = 50) String protocolVersion,
        @NotBlank @Size(max = 30) String phase,
        @NotBlank @Size(max = 255) String sponsor,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        @Size(max = 2000) String description) {}
