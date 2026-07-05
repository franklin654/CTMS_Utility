package com.ctms.ctms_backend.document.repository;

import com.ctms.ctms_backend.document.entity.DocumentReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {

    List<DocumentReview> findByDocumentVersionIdOrderByActedAtDesc(Long documentVersionId);
}
