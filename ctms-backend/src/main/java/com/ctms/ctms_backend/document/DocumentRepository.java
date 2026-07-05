package com.ctms.ctms_backend.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByCategory(String category, Pageable pageable);
}
