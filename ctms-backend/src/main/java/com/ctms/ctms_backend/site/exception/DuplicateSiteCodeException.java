package com.ctms.ctms_backend.site.exception;

public class DuplicateSiteCodeException extends RuntimeException {
    public DuplicateSiteCodeException(String siteCode) {
        super("Site code already in use: " + siteCode);
    }
}
