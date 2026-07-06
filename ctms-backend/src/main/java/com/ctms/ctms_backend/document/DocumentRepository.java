package com.ctms.ctms_backend.document;

import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByCategory(String category, Pageable pageable);

    /** Backs DocumentRequirementService's Study-phase-transition guard -- true only if the study
     * has a document of this category whose CURRENT version has actually been approved. */
    boolean existsByStudyIdAndCategoryAndCurrentVersionStatus(Long studyId, String category, DocumentVersionStatus status);

    /** Excludes documents whose category is DENY-listed for any of the caller's roles, at the DB
     * level (not a post-fetch Java filter, which would break Pageable counts) -- Story 05. */
    @Query("SELECT d FROM Document d WHERE d.category IS NULL OR d.category NOT IN "
            + "(SELECT r.category FROM DocumentCategoryAccessRule r "
            + "WHERE r.roleCode IN :roleCodes AND r.access = 'DENY')")
    Page<Document> findVisibleTo(@Param("roleCodes") Collection<String> roleCodes, Pageable pageable);

    /** Patient Portal (Epic 10 Story 04) list -- same DENY-list filtering as {@link #findVisibleTo},
     * scoped to documents this specific patient uploaded (Document.owner, already set correctly to
     * the uploading patient's own User by PatientDocumentUploadService). Deliberately NOT
     * study-scoped: two patients enrolled in the same study must never see each other's personal
     * document uploads, only their own. */
    @Query("SELECT d FROM Document d WHERE d.owner.id = :ownerId AND (d.category IS NULL OR d.category NOT IN "
            + "(SELECT r.category FROM DocumentCategoryAccessRule r "
            + "WHERE r.roleCode IN :roleCodes AND r.access = 'DENY'))")
    Page<Document> findByOwnerIdAndVisibleTo(
            @Param("ownerId") Long ownerId, @Param("roleCodes") Collection<String> roleCodes, Pageable pageable);
}
