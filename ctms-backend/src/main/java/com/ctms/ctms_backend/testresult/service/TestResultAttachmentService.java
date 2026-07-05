package com.ctms.ctms_backend.testresult.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.StorageService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.testresult.dto.TestResultAttachmentResponse;
import com.ctms.ctms_backend.testresult.entity.TestResult;
import com.ctms.ctms_backend.testresult.entity.TestResultAttachment;
import com.ctms.ctms_backend.testresult.exception.TestResultAttachmentNotFoundException;
import com.ctms.ctms_backend.testresult.repository.TestResultAttachmentRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** BL-07 (not part of backlog v0.2, reintroduced per Phase 7 scope decision). Mirrors
 * DocumentService's exact storeVersion/downloadVersion pattern -- same StorageService, same
 * SHA-256 checksum computation. */
@Service
public class TestResultAttachmentService {

    private final TestResultAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public TestResultAttachmentService(
            TestResultAttachmentRepository attachmentRepository,
            UserRepository userRepository,
            StorageService storageService,
            AuditService auditService) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional
    public TestResultAttachmentResponse upload(TestResult testResult, MultipartFile file, String actorUsername) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
        String checksum = sha256(bytes);
        String storageKey = storageService.store(new ByteArrayInputStream(bytes), file.getOriginalFilename());
        User actor = currentUser(actorUsername);

        TestResultAttachment attachment = new TestResultAttachment();
        attachment.setTestResult(testResult);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setStoragePath(storageKey);
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setChecksumSha256(checksum);
        attachment.setUploadedBy(actor);
        attachment = attachmentRepository.save(attachment);

        auditService.record(
                "TestResultAttachment", String.valueOf(attachment.getId()), AuditAction.CREATE,
                null, "uploaded " + attachment.getFileName() + " for test result " + testResult.getId(), null);

        return TestResultAttachmentResponse.from(attachment);
    }

    @Transactional(readOnly = true)
    public List<TestResultAttachmentResponse> list(Long testResultId) {
        return attachmentRepository.findByTestResultId(testResultId).stream()
                .map(TestResultAttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestResultAttachment findAttachment(Long id) {
        return attachmentRepository.findById(id).orElseThrow(() -> new TestResultAttachmentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public InputStream download(Long attachmentId) {
        TestResultAttachment attachment = findAttachment(attachmentId);
        auditService.record(
                "TestResultAttachment", String.valueOf(attachmentId), AuditAction.ACCESS,
                null, "downloaded " + attachment.getFileName(), null);
        return storageService.retrieve(attachment.getStoragePath());
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
