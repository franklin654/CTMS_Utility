package com.ctms.ctms_backend.site.repository;

import com.ctms.ctms_backend.site.entity.Site;
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
              and (:search = ''
                   or lower(s.name) like lower(concat('%', :search, '%'))
                   or lower(s.siteCode) like lower(concat('%', :search, '%')))
            """)
    Page<Site> search(@Param("studyId") Long studyId, @Param("search") String search, Pageable pageable);
}
