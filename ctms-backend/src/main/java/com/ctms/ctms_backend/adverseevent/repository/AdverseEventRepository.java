package com.ctms.ctms_backend.adverseevent.repository;

import com.ctms.ctms_backend.adverseevent.entity.AdverseEvent;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventSeverity;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdverseEventRepository extends JpaRepository<AdverseEvent, Long> {

    List<AdverseEvent> findBySubjectIdOrderByCreatedAtDesc(Long subjectId);

    /** Returns (siteId, count) pairs of open high-severity AEs, for the high-risk-site rule. */
    @Query("""
            select ae.subject.site.id, count(ae)
            from AdverseEvent ae
            where ae.subject.site.id in :siteIds
              and ae.severity in :severities
              and ae.status <> :excludedStatus
            group by ae.subject.site.id
            """)
    List<Object[]> countHighSeverityOpenGroupedBySite(
            @Param("siteIds") List<Long> siteIds,
            @Param("severities") List<AdverseEventSeverity> severities,
            @Param("excludedStatus") AdverseEventStatus excludedStatus);
}
