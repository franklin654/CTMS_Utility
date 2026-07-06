package com.ctms.ctms_backend.patientportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.document.DocumentResponse;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.dto.DocumentReviewResponse;
import com.ctms.ctms_backend.document.service.DocumentWorkflowService;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.subject.entity.Subject;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PatientDocumentUploadServiceTest {

    @Mock private DocumentService documentService;
    @Mock private DocumentWorkflowService documentWorkflowService;

    @InjectMocks
    private PatientDocumentUploadService uploadService;

    @Test
    void upload_createsPendingReviewDocument_thenSubmitsForReview() {
        Study study = new Study();
        study.setId(10L);
        Subject subject = new Subject();
        subject.setStudy(study);

        DocumentResponse created = new DocumentResponse(5L, "Lab Report", "LAB_RESULTS", "patient1", 10L, "ST-000010", null, null, null, null, null);
        when(documentService.createPendingReviewDocument(
                        eq("Lab Report"), eq("LAB_RESULTS"), eq(10L), any(LocalDate.class), eq("patient1"), any()))
                .thenReturn(created);
        when(documentWorkflowService.submitForReview(5L, 1, "patient1"))
                .thenReturn(new DocumentReviewResponse(1L, "REVIEW", "SUBMITTED", null, "patient1", null, false));

        MockMultipartFile file = new MockMultipartFile("file", "lab.pdf", "application/pdf", "content".getBytes());
        DocumentResponse response = uploadService.upload(
                subject, "LAB_RESULTS", "Lab Report", LocalDate.of(2026, 1, 1), file, "patient1");

        assertEquals(5L, response.id());
        verify(documentWorkflowService).submitForReview(5L, 1, "patient1");
    }
}
