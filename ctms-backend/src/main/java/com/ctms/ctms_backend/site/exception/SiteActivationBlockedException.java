package com.ctms.ctms_backend.site.exception;

import java.util.List;

/** Thrown by an explicit "attempt activation" call (Epic 2 Story 03) when one or more
 * checklist prerequisites are still incomplete. Carries the missing-item labels so the
 * frontend can render a clear blocking list (Story 03 AC2) instead of parsing a message string. */
public class SiteActivationBlockedException extends RuntimeException {

    private final List<String> missingItems;

    public SiteActivationBlockedException(List<String> missingItems) {
        super("Site cannot be activated -- missing prerequisites: " + String.join(", ", missingItems));
        this.missingItems = missingItems;
    }

    public List<String> getMissingItems() {
        return missingItems;
    }
}
