package com.ctms.ctms_backend.subject.repository;

import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /** Resolves the Patient Portal's "which subject am I" -- the only place a patient's identity
     * is ever looked up, always keyed off the authenticated username, never a client-supplied ID. */
    Optional<Subject> findByLinkedUser_Username(String username);

    @Query("""
            select s from Subject s
            where (:studyId is null or s.study.id = :studyId)
              and (:siteId is null or s.site.id = :siteId)
              and (:status is null or s.status = :status)
              and (:search = ''
                   or lower(s.firstName) like lower(concat('%', :search, '%'))
                   or lower(s.lastName) like lower(concat('%', :search, '%'))
                   or lower(s.subjectCode) like lower(concat('%', :search, '%')))
            """)
    Page<Subject> search(
            @Param("studyId") Long studyId,
            @Param("siteId") Long siteId,
            @Param("status") SubjectStatus status,
            @Param("search") String search,
            Pageable pageable);

    List<Subject> findByStudyIdAndStatusNotIn(Long studyId, List<SubjectStatus> excludedStatuses);

    long countBySiteIdInAndStatus(List<Long> siteIds, SubjectStatus status);
}
