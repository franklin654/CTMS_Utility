package com.ctms.ctms_backend.site.dto;

import java.util.List;

public record ActivationAttemptResponse(boolean activated, List<String> missingItems, SiteResponse site) {}
