package com.ctms.ctms_backend.monitoring.exception;

public class MonitoringVisitReportNotFoundException extends RuntimeException {

    public MonitoringVisitReportNotFoundException(Long id) {
        super("Monitoring visit report not found: " + id);
    }
}
