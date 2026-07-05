package com.ctms.ctms_backend.testresult.controller;

import com.ctms.ctms_backend.testresult.dto.TestResultAttachmentResponse;
import com.ctms.ctms_backend.testresult.entity.TestResult;
import com.ctms.ctms_backend.testresult.entity.TestResultAttachment;
import com.ctms.ctms_backend.testresult.service.TestResultAttachmentService;
import com.ctms.ctms_backend.testresult.service.TestResultService;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TestResultAttachmentController {

    private static final String WRITE_ROLES = "hasAnyRole('SITE_COORDINATOR','INVESTIGATOR','STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final TestResultAttachmentService attachmentService;
    private final TestResultService testResultService;

    public TestResultAttachmentController(TestResultAttachmentService attachmentService, TestResultService testResultService) {
        this.attachmentService = attachmentService;
        this.testResultService = testResultService;
    }

    @PostMapping(value = "/api/test-results/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    public TestResultAttachmentResponse upload(
            Principal principal, @PathVariable Long id, @RequestPart MultipartFile file) {
        TestResult testResult = testResultService.findTestResult(id);
        return attachmentService.upload(testResult, file, principal.getName());
    }

    @GetMapping("/api/test-results/{id}/attachments")
    @PreAuthorize(READ_ROLES)
    public List<TestResultAttachmentResponse> list(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @GetMapping("/api/attachments/{id}/download")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        TestResultAttachment attachment = attachmentService.findAttachment(id);
        InputStream content = attachmentService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(new InputStreamResource(content));
    }
}
