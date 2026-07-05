package com.ctms.ctms_backend.document;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
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
    private final AuditService auditService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            StorageService storageService,
            UserRepository userRepository,
            AuditService auditService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.storageService = storageService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentResponse createDocument(String title, String category, String uploaderUsername, MultipartFile file) {
        User uploader = currentUser(uploaderUsername);

        Document document = new Document();
        document.setTitle(title);
        document.setCategory(category);
        document.setOwner(uploader);
        document = documentRepository.save(document);

        DocumentVersion version = storeVersion(document, file, uploader, 1);
        document.setCurrentVersion(version);
        document = documentRepository.save(document);

        auditService.record("Document", String.valueOf(document.getId()), AuditAction.CREATE, null, version.getFileName(), null);
        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentVersionResponse addVersion(Long documentId, String uploaderUsername, MultipartFile file) {
        Document document = documentRepository.findById(documentId).orElseThrow(NoSuchElementException::new);
        User uploader = currentUser(uploaderUsername);

        int nextVersionNumber = documentVersionRepository.countByDocumentId(documentId) + 1;
        DocumentVersion previousCurrent = document.getCurrentVersion();
        if (previousCurrent != null) {
            previousCurrent.setStatus(DocumentVersion.STATUS_ARCHIVED);
            documentVersionRepository.save(previousCurrent);
        }

        DocumentVersion version = storeVersion(document, file, uploader, nextVersionNumber);
        document.setCurrentVersion(version);
        documentRepository.save(document);

        auditService.record(
                "Document", String.valueOf(documentId), AuditAction.UPDATE, null,
                "new version " + nextVersionNumber + ": " + version.getFileName(), null);
        return DocumentVersionResponse.from(version);
    }

    private DocumentVersion storeVersion(Document document, MultipartFile file, User uploader, int versionNumber) {
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
        version.setStatus(DocumentVersion.STATUS_CURRENT);
        version.setUploadedBy(uploader);
        return documentVersionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public InputStream downloadVersion(Long documentId, int versionNumber) {
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
        return documentRepository.findAll(pageable).map(DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long documentId) {
        return DocumentResponse.from(documentRepository.findById(documentId).orElseThrow(NoSuchElementException::new));
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
