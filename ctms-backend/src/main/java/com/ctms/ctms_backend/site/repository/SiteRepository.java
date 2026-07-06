package com.ctms.ctms_backend.site.repository;

import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteRepository extends JpaRepository<Site, Long> {

    boolean existsBySiteCode(String siteCode);

    @Query("""
            select s from Site s
            where (:studyId is null or s.study.id = :studyId)
              and (:status is null or s.status = :status)
              and (:search = ''
                   or lower(s.name) like lower(concat('%', :search, '%'))
                   or lower(s.siteCode) like lower(concat('%', :search, '%')))
            """)
    Page<Site> search(
            @Param("studyId") Long studyId, @Param("status") SiteStatus status, @Param("search") String search, Pageable pageable);

    @Query("""
            select s from Site s
            where (:studyId is null or s.study.id = :studyId)
              and (:country is null or s.country = :country)
              and (:siteId is null or s.id = :siteId)
              and (:phase is null or s.study.phase = :phase)
            """)
    List<Site> findForDashboard(
            @Param("studyId") Long studyId,
            @Param("country") String country,
            @Param("siteId") Long siteId,
            @Param("phase") String phase);

    @Query("select s from Site s where s.assignedCra.id = :craId or s.backupCra.id = :craId")
    List<Site> findByAssignedOrBackupCra(@Param("craId") Long craId);

    @Query("select distinct s.country from Site s order by s.country")
    List<String> findDistinctCountries();
}
