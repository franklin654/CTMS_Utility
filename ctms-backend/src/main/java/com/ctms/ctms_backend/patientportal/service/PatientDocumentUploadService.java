package com.ctms.ctms_backend.patientportal.service;

import com.ctms.ctms_backend.document.DocumentResponse;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.service.DocumentWorkflowService;
import com.ctms.ctms_backend.subject.entity.Subject;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** BL Epic 10 Story 04 (Upload Patient Documents). Per the confirmed trust-boundary decision,
 * a patient's upload does NOT become the document's CURRENT version instantly -- it starts at
 * DRAFT (via DocumentService.createPendingReviewDocument) and is immediately submitted into
 * Phase 2's existing review workflow, landing directly in staff's existing approval queue with
 * zero changes to that queue or its approval logic. */
@Service
public class PatientDocumentUploadService {

    private final DocumentService documentService;
    private final DocumentWorkflowService documentWorkflowService;

    public PatientDocumentUploadService(DocumentService documentService, DocumentWorkflowService documentWorkflowService) {
        this.documentService = documentService;
        this.documentWorkflowService = documentWorkflowService;
    }

    @Transactional
    public DocumentResponse upload(
            Subject subject, String category, String title, LocalDate effectiveDate, MultipartFile file, String actorUsername) {
        DocumentResponse created = documentService.createPendingReviewDocument(
                title, category, subject.getStudy().getId(), effectiveDate, actorUsername, file);
        documentWorkflowService.submitForReview(created.id(), 1, actorUsername);
        return created;
    }
}
