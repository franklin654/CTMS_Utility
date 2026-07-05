package com.ctms.ctms_backend.site.exception;

public class ChecklistItemNotFoundException extends RuntimeException {
    public ChecklistItemNotFoundException(Long siteId, String itemType) {
        super("Checklist item not found: site=" + siteId + " itemType=" + itemType);
    }
}
