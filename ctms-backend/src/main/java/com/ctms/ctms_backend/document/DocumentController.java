package com.ctms.ctms_backend.document;

import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Category-level read restrictions (Story 05) are enforced inside DocumentService/
 * DocumentAccessControlService, not here -- every role can hit these endpoints, but denied-category
 * documents are filtered out of list() or rejected (403) from get()/download(). */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER','SITE_COORDINATOR','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','SITE_COORDINATOR','ADMIN','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP','PATIENT_SUBJECT')";

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<DocumentResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public DocumentResponse get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(READ_ROLES)
    public List<DocumentVersionResponse> versions(@PathVariable Long id) {
        return documentService.versionHistory(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    public DocumentResponse upload(
            Principal principal,
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long studyId,
            @RequestPart MultipartFile file) {
        return documentService.createDocument(title, category, studyId, principal.getName(), file);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    public DocumentVersionResponse uploadVersion(
            Principal principal, @PathVariable Long id, @RequestPart MultipartFile file) {
        return documentService.addVersion(id, principal.getName(), file);
    }

    @GetMapping("/{id}/versions/{versionNumber}/download")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id, @PathVariable int versionNumber) {
        InputStream content = documentService.downloadVersion(id, versionNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(content));
    }
}
