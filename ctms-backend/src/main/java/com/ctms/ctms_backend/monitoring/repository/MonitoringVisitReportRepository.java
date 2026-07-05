package com.ctms.ctms_backend.monitoring.repository;

import com.ctms.ctms_backend.monitoring.entity.MonitoringVisitReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringVisitReportRepository extends JpaRepository<MonitoringVisitReport, Long> {

    List<MonitoringVisitReport> findByMonitoringVisitId(Long monitoringVisitId);
}
