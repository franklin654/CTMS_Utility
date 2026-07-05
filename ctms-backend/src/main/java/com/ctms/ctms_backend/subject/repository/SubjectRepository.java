package com.ctms.ctms_backend.subject.repository;

import com.ctms.ctms_backend.subject.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    @Query("""
            select s from Subject s
            where (:studyId is null or s.study.id = :studyId)
              and (:siteId is null or s.site.id = :siteId)
              and (:search = ''
                   or lower(s.firstName) like lower(concat('%', :search, '%'))
                   or lower(s.lastName) like lower(concat('%', :search, '%'))
                   or lower(s.subjectCode) like lower(concat('%', :search, '%')))
            """)
    Page<Subject> search(
            @Param("studyId") Long studyId,
            @Param("siteId") Long siteId,
            @Param("search") String search,
            Pageable pageable);
}
