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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public Page<DocumentResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.list(pageable);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<DocumentVersionResponse> versions(@PathVariable Long id) {
        return documentService.versionHistory(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(
            Principal principal,
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestPart MultipartFile file) {
        return documentService.createDocument(title, category, principal.getName(), file);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentVersionResponse uploadVersion(
            Principal principal, @PathVariable Long id, @RequestPart MultipartFile file) {
        return documentService.addVersion(id, principal.getName(), file);
    }

    @GetMapping("/{id}/versions/{versionNumber}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id, @PathVariable int versionNumber) {
        InputStream content = documentService.downloadVersion(id, versionNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(content));
    }
}
