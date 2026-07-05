package com.ctms.ctms_backend.monitoring.repository;

import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringVisitRepository extends JpaRepository<MonitoringVisit, Long> {

    List<MonitoringVisit> findBySiteIdOrderByVisitDateDesc(Long siteId);

    long countBySiteIdIn(List<Long> siteIds);
}
