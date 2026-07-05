package com.ctms.ctms_backend.visit.repository;

import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    List<Visit> findBySubjectIdOrderByScheduledDateAsc(Long subjectId);

    List<Visit> findByVisitTemplateIdAndStatus(Long visitTemplateId, VisitStatus status);

    /** Used by VisitService's dependency guard to find a subject's visit scheduled from a
     * specific (prerequisite) template, to check whether it's already COMPLETED. */
    Optional<Visit> findBySubjectIdAndVisitTemplateId(Long subjectId, Long visitTemplateId);

    List<Visit> findByStatusAndScheduledDate(VisitStatus status, LocalDate scheduledDate);

    List<Visit> findByStatusAndScheduledDateLessThan(VisitStatus status, LocalDate cutoffDate);

    /** Returns (siteId, status, count) triples for dashboard aggregation / high-risk-site
     * evaluation -- grouped so per-site missed-visit rate can be computed without N+1 queries. */
    @Query("""
            select v.subject.site.id, v.status, count(v)
            from Visit v
            where v.subject.site.id in :siteIds
            group by v.subject.site.id, v.status
            """)
    List<Object[]> countByStatusGroupedBySite(@Param("siteIds") List<Long> siteIds);
}
