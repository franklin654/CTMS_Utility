package com.ctms.ctms_backend.document;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.service.DocumentAccessControlService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final StudyRepository studyRepository;
    private final SubjectRepository subjectRepository;
    private final AuditService auditService;
    private final DocumentAccessControlService accessControlService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            StorageService storageService,
            UserRepository userRepository,
            StudyRepository studyRepository,
            SubjectRepository subjectRepository,
            AuditService auditService,
            DocumentAccessControlService accessControlService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.storageService = storageService;
        this.userRepository = userRepository;
        this.studyRepository = studyRepository;
        this.subjectRepository = subjectRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
    }

    /** The first version of a new document is trusted content from its own upload (no prior
     * CURRENT version to protect) and goes straight to CURRENT; only *additional* versions must
     * pass through the Phase 2 approval workflow before superseding it (see {@link #addVersion}). */
    @Transactional
    public DocumentResponse createDocument(
            String title, String category, Long studyId, Long subjectId, String uploaderUsername, MultipartFile file) {
        User uploader = currentUser(uploaderUsername);

        Document document = new Document();
        document.setTitle(title);
        document.setCategory(category);
        document.setOwner(uploader);
        if (studyId != null) {
            document.setStudy(resolveStudy(studyId));
        }
        if (subjectId != null) {
            document.setSubject(resolveSubject(subjectId));
        }
        document = documentRepository.save(document);

        DocumentVersion version = storeVersion(document, file, uploader, 1, DocumentVersionStatus.CURRENT);
        document.setCurrentVersion(version);
        document = documentRepository.save(document);

        auditService.record("Document", String.valueOf(document.getId()), AuditAction.CREATE, null, version.getFileName(), null);
        return DocumentResponse.from(document);
    }

    /** Patient Portal entry point (Epic 10 Story 04). Unlike {@link #createDocument}, the first
     * version does NOT go straight to CURRENT -- patient-submitted content is a different trust
     * boundary than an internal staff upload, so it starts at DRAFT just like an ordinary
     * *additional* version, and the caller (PatientDocumentUploadService) is responsible for
     * calling DocumentWorkflowService.submitForReview immediately after, landing it in staff's
     * existing approval queue unchanged. */
    @Transactional
    public DocumentResponse createPendingReviewDocument(
            String title, String category, Long studyId, LocalDate effectiveDate, String uploaderUsername, MultipartFile file) {
        User uploader = currentUser(uploaderUsername);

        Document document = new Document();
        document.setTitle(title);
        document.setCategory(category);
        document.setOwner(uploader);
        if (studyId != null) {
            document.setStudy(resolveStudy(studyId));
        }
        document = documentRepository.save(document);

        DocumentVersion version = storeVersion(document, file, uploader, 1, DocumentVersionStatus.DRAFT);
        version.setEffectiveDate(effectiveDate);
        documentVersionRepository.save(version);

        auditService.record(
                "Document", String.valueOf(document.getId()), AuditAction.CREATE, null,
                "patient-submitted (pending review): " + version.getFileName(), null);
        return DocumentResponse.from(document);
    }

    /** New versions start as DRAFT and must pass through DocumentWorkflowService's
     * submit-for-review / reviewer-approve / approver-final-approve steps before superseding the
     * current version -- unlike Phase 0's original behavior, this no longer auto-promotes. */
    @Transactional
    public DocumentVersionResponse addVersion(Long documentId, String uploaderUsername, MultipartFile file) {
        Document document = documentRepository.findById(documentId).orElseThrow(NoSuchElementException::new);
        User uploader = currentUser(uploaderUsername);

        int nextVersionNumber = documentVersionRepository.countByDocumentId(documentId) + 1;
        DocumentVersion version = storeVersion(document, file, uploader, nextVersionNumber, DocumentVersionStatus.DRAFT);

        auditService.record(
                "Document", String.valueOf(documentId), AuditAction.UPDATE, null,
                "new version " + nextVersionNumber + ": " + version.getFileName(), null);
        return DocumentVersionResponse.from(version);
    }

    /** Archives the document's current CURRENT version (if any) and promotes {@code newVersion} in
     * its place. Called both by {@link #createDocument} implicitly (no prior version to archive)
     * and by DocumentWorkflowService's final-approval step once a new version clears the workflow. */
    public void promoteToCurrent(DocumentVersion newVersion) {
        Document document = newVersion.getDocument();
        DocumentVersion previousCurrent = document.getCurrentVersion();
        if (previousCurrent != null && !previousCurrent.getId().equals(newVersion.getId())) {
            previousCurrent.setStatus(DocumentVersionStatus.ARCHIVED);
            documentVersionRepository.save(previousCurrent);
        }
        document.setCurrentVersion(newVersion);
        documentRepository.save(document);
    }

    private DocumentVersion storeVersion(
            Document document, MultipartFile file, User uploader, int versionNumber, DocumentVersionStatus initialStatus) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
        String checksum = sha256(bytes);
        String storageKey = storageService.store(new ByteArrayInputStream(bytes), file.getOriginalFilename());

        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(versionNumber);
        version.setFileName(file.getOriginalFilename());
        version.setStoragePath(storageKey);
        version.setContentType(file.getContentType());
        version.setSizeBytes(file.getSize());
        version.setChecksumSha256(checksum);
        version.setStatus(initialStatus);
        version.setUploadedBy(uploader);
        return documentVersionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public InputStream downloadVersion(Long documentId, int versionNumber) {
        Document document = documentRepository.findById(documentId).orElseThrow(NoSuchElementException::new);
        accessControlService.assertReadable(document);
        DocumentVersion version = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(NoSuchElementException::new);
        auditService.record(
                "Document", String.valueOf(documentId), AuditAction.ACCESS, null,
                "downloaded version " + versionNumber, null);
        return storageService.retrieve(version.getStoragePath());
    }

    @Transactional(readOnly = true)
    public DocumentVersionResponse currentVersionMetadata(Long documentId) {
        Document document = documentRepository.findById(documentId).orElseThrow(NoSuchElementException::new);
        if (document.getCurrentVersion() == null) {
            throw new NoSuchElementException();
        }
        return DocumentVersionResponse.from(document.getCurrentVersion());
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> versionHistory(Long documentId) {
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(DocumentVersionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(Pageable pageable) {
        return documentRepository.findVisibleTo(accessControlService.currentRoleCodes(), pageable)
                .map(DocumentResponse::from);
    }

    /** Patient Portal (Epic 10 Story 04) list -- same DENY-list filtering as {@link #list}, scoped
     * to documents this specific patient (User) uploaded -- NOT the whole study, since two
     * patients enrolled in the same study must never see each other's personal uploads. */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listByOwner(Long ownerUserId, Pageable pageable) {
        return documentRepository.findByOwnerIdAndVisibleTo(ownerUserId, accessControlService.currentRoleCodes(), pageable)
                .map(DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long documentId) {
        Document document = documentRepository.findById(documentId).orElseThrow(NoSuchElementException::new);
        accessControlService.assertReadable(document);
        return DocumentResponse.from(document);
    }

    private Study resolveStudy(Long studyId) {
        return studyRepository.findById(studyId).orElseThrow(NoSuchElementException::new);
    }

    private Subject resolveSubject(Long subjectId) {
        return subjectRepository.findById(subjectId).orElseThrow(() -> new SubjectNotFoundException(subjectId));
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
