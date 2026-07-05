package com.ctms.ctms_backend.site.exception;

public class SiteNotFoundException extends RuntimeException {
    public SiteNotFoundException(Long id) {
        super("Site not found: " + id);
    }
}
