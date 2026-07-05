package com.ctms.ctms_backend.monitoring.exception;

public class MonitoringVisitNotFoundException extends RuntimeException {

    public MonitoringVisitNotFoundException(Long id) {
        super("Monitoring visit not found: " + id);
    }
}
