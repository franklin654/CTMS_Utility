package com.ctms.ctms_backend.patientportal.controller;

import com.ctms.ctms_backend.document.DocumentResponse;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.patientportal.service.PatientContextService;
import com.ctms.ctms_backend.patientportal.service.PatientDocumentUploadService;
import com.ctms.ctms_backend.subject.entity.Subject;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** BL Epic 10 Story 04 (Upload Patient Documents). List reuses DocumentService's existing
 * DENY-list category filtering (Story 05, Phase 2), scoped to documents this patient personally
 * uploaded -- NOT the whole study, since another patient enrolled in the same study must never
 * see this patient's personal document uploads. */
@RestController
@RequestMapping("/api/patient/documents")
@PreAuthorize("hasRole('PATIENT_SUBJECT')")
public class PatientDocumentController {

    private final PatientContextService patientContextService;
    private final DocumentService documentService;
    private final PatientDocumentUploadService uploadService;

    public PatientDocumentController(
            PatientContextService patientContextService, DocumentService documentService, PatientDocumentUploadService uploadService) {
        this.patientContextService = patientContextService;
        this.documentService = documentService;
        this.uploadService = uploadService;
    }

    @GetMapping
    public Page<DocumentResponse> list(Principal principal, @PageableDefault(size = 20) Pageable pageable) {
        // Resolve via the Subject to confirm the caller genuinely has a linked patient record
        // (consistent with every other endpoint here), then scope by their own User id.
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return documentService.listByOwner(subject.getLinkedUser().getId(), pageable);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(
            Principal principal,
            @RequestParam @NotBlank String category,
            @RequestParam @NotBlank String title,
            @RequestParam(required = false) LocalDate effectiveDate,
            @RequestPart MultipartFile file) {
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return uploadService.upload(subject, category, title, effectiveDate, file, principal.getName());
    }
}
