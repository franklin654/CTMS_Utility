package com.ctms.ctms_backend.visit.repository;

import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    List<Visit> findBySubjectIdOrderByScheduledDateAsc(Long subjectId);

    List<Visit> findByVisitTemplateIdAndStatus(Long visitTemplateId, VisitStatus status);

    List<Visit> findByStatusAndScheduledDate(VisitStatus status, LocalDate scheduledDate);

    List<Visit> findByStatusAndScheduledDateLessThan(VisitStatus status, LocalDate cutoffDate);
}
