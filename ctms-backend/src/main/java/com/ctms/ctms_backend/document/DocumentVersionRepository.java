package com.ctms.ctms_backend.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, int versionNumber);

    int countByDocumentId(Long documentId);
}
