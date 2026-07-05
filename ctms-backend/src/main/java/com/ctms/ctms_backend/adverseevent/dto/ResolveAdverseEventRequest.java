package com.ctms.ctms_backend.adverseevent.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveAdverseEventRequest(@NotBlank String resolutionNotes) {}
