package com.ctms.ctms_backend.document.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.Document;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.DocumentVersion;
import com.ctms.ctms_backend.document.DocumentVersionRepository;
import com.ctms.ctms_backend.document.DocumentVersionResponse;
import com.ctms.ctms_backend.document.dto.DocumentReviewResponse;
import com.ctms.ctms_backend.document.dto.FinalApprovalRequest;
import com.ctms.ctms_backend.document.dto.ReviewDecisionRequest;
import com.ctms.ctms_backend.document.entity.DocumentReview;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.entity.ReviewAction;
import com.ctms.ctms_backend.document.entity.ReviewStage;
import com.ctms.ctms_backend.document.exception.DocumentAccessDeniedException;
import com.ctms.ctms_backend.document.exception.DocumentVersionNotFoundException;
import com.ctms.ctms_backend.document.exception.InvalidDocumentTransitionException;
import com.ctms.ctms_backend.document.repository.DocumentReviewRepository;
import com.ctms.ctms_backend.document.repository.DocumentWorkflowRoleRepository;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the document-version approval state machine: DRAFT -> PENDING_REVIEW ->
 * PENDING_APPROVAL -> CURRENT, with REJECTED reachable (terminal) from either review stage.
 * Reviewer/approver roles are read from {@link DocumentWorkflowRoleRepository} at call time
 * (data-driven per CLAUDE.md 2.7), layered under the coarser static @PreAuthorize on the
 * controller. Final approval reuses Phase 0/1's ESignatureService, per CLAUDE.md 2.5.
 */
@Service
public class DocumentWorkflowService {

    private static final Map<DocumentVersionStatus, Set<DocumentVersionStatus>> ALLOWED_TRANSITIONS = Map.of(
            DocumentVersionStatus.DRAFT, Set.of(DocumentVersionStatus.PENDING_REVIEW),
            DocumentVersionStatus.PENDING_REVIEW,
                    Set.of(DocumentVersionStatus.PENDING_APPROVAL, DocumentVersionStatus.REJECTED, DocumentVersionStatus.DRAFT),
            DocumentVersionStatus.PENDING_APPROVAL, Set.of(DocumentVersionStatus.CURRENT, DocumentVersionStatus.REJECTED));

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentReviewRepository documentReviewRepository;
    private final DocumentWorkflowRoleRepository documentWorkflowRoleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ESignatureService eSignatureService;
    private final NotificationService notificationService;
    private final DocumentService documentService;

    public DocumentWorkflowService(
            DocumentVersionRepository documentVersionRepository,
            DocumentReviewRepository documentReviewRepository,
            DocumentWorkflowRoleRepository documentWorkflowRoleRepository,
            UserRepository userRepository,
            AuditService auditService,
            ESignatureService eSignatureService,
            NotificationService notificationService,
            DocumentService documentService) {
        this.documentVersionRepository = documentVersionRepository;
        this.documentReviewRepository = documentReviewRepository;
        this.documentWorkflowRoleRepository = documentWorkflowRoleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.eSignatureService = eSignatureService;
        this.notificationService = notificationService;
        this.documentService = documentService;
    }

    @Transactional
    public DocumentReviewResponse submitForReview(Long documentId, int versionNumber, String actorUsername) {
        DocumentVersion version = findVersion(documentId, versionNumber);
        assertTransition(version.getStatus(), DocumentVersionStatus.PENDING_REVIEW);
        User actor = currentUser(actorUsername);

        version.setStatus(DocumentVersionStatus.PENDING_REVIEW);
        documentVersionRepository.save(version);

        DocumentReview review = newReview(version, ReviewStage.REVIEW, ReviewAction.SUBMITTED, null, actor, null);
        documentReviewRepository.save(review);

        auditService.record("Document", String.valueOf(documentId), AuditAction.STATE_CHANGE,
                DocumentVersionStatus.DRAFT.name(), DocumentVersionStatus.PENDING_REVIEW.name(), null);
        notifyRoleHolders(reviewerRoleFor(version.getDocument()), version, "submitted for review");

        return DocumentReviewResponse.from(review);
    }

    @Transactional
    public DocumentReviewResponse reviewerDecide(
            Long documentId, int versionNumber, ReviewDecisionRequest req, String actorUsername) {
        DocumentVersion version = findVersion(documentId, versionNumber);
        assertRoleForStage(version.getDocument(), ReviewStage.REVIEW, actorUsername);

        ReviewAction action = parseAction(req.action());
        DocumentVersionStatus target = switch (action) {
            case APPROVED -> DocumentVersionStatus.PENDING_APPROVAL;
            case REJECTED -> DocumentVersionStatus.REJECTED;
            case CHANGES_REQUESTED -> DocumentVersionStatus.DRAFT;
            case SUBMITTED -> throw new InvalidDocumentTransitionException("SUBMITTED is not a reviewer decision");
        };
        if (action != ReviewAction.APPROVED && (req.comment() == null || req.comment().isBlank())) {
            throw new InvalidDocumentTransitionException("A comment is required for " + action);
        }
        assertTransition(version.getStatus(), target);

        User actor = currentUser(actorUsername);
        version.setStatus(target);
        documentVersionRepository.save(version);

        DocumentReview review = newReview(version, ReviewStage.REVIEW, action, req.comment(), actor, null);
        documentReviewRepository.save(review);

        auditService.record("Document", String.valueOf(documentId), AuditAction.STATE_CHANGE,
                DocumentVersionStatus.PENDING_REVIEW.name(), target.name(), req.comment());

        if (target == DocumentVersionStatus.PENDING_APPROVAL) {
            notifyRoleHolders(approverRoleFor(version.getDocument()), version, "awaiting final approval");
        }
        return DocumentReviewResponse.from(review);
    }

    @Transactional
    public DocumentReviewResponse approverFinalDecide(
            Long documentId, int versionNumber, FinalApprovalRequest req, String actorUsername) {
        DocumentVersion version = findVersion(documentId, versionNumber);
        assertRoleForStage(version.getDocument(), ReviewStage.APPROVAL, actorUsername);

        ReviewAction action = parseAction(req.action());
        if (action != ReviewAction.APPROVED && action != ReviewAction.REJECTED) {
            throw new InvalidDocumentTransitionException("Approval stage only accepts APPROVED or REJECTED");
        }
        DocumentVersionStatus target = action == ReviewAction.APPROVED
                ? DocumentVersionStatus.CURRENT
                : DocumentVersionStatus.REJECTED;
        if (action == ReviewAction.REJECTED && (req.comment() == null || req.comment().isBlank())) {
            throw new InvalidDocumentTransitionException("A comment is required to reject");
        }
        assertTransition(version.getStatus(), target);

        User actor = currentUser(actorUsername);
        ESignature signature = null;
        if (action == ReviewAction.APPROVED) {
            signature = eSignatureService.sign(
                    actorUsername, req.password(), "DocumentVersion", String.valueOf(version.getId()), req.reason());
        }

        DocumentReview review = newReview(version, ReviewStage.APPROVAL, action, req.comment(), actor, signature);
        documentReviewRepository.save(review);

        if (action == ReviewAction.APPROVED) {
            version.setStatus(DocumentVersionStatus.CURRENT);
            documentVersionRepository.save(version);
            documentService.promoteToCurrent(version);
        } else {
            version.setStatus(DocumentVersionStatus.REJECTED);
            documentVersionRepository.save(version);
        }

        auditService.record("Document", String.valueOf(documentId), AuditAction.STATE_CHANGE,
                DocumentVersionStatus.PENDING_APPROVAL.name(), target.name(), req.comment());

        return DocumentReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public Page<DocumentVersionResponse> approvalQueue(ReviewStage stage, Pageable pageable) {
        DocumentVersionStatus status = stage == ReviewStage.REVIEW
                ? DocumentVersionStatus.PENDING_REVIEW
                : DocumentVersionStatus.PENDING_APPROVAL;
        return documentVersionRepository.findByStatus(status, pageable).map(DocumentVersionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DocumentReviewResponse> history(Long documentId, int versionNumber) {
        DocumentVersion version = findVersion(documentId, versionNumber);
        return documentReviewRepository.findByDocumentVersionIdOrderByActedAtDesc(version.getId()).stream()
                .map(DocumentReviewResponse::from)
                .toList();
    }

    private void assertTransition(DocumentVersionStatus from, DocumentVersionStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidDocumentTransitionException(from, to);
        }
    }

    /** Coarse @PreAuthorize on the controller only narrows to "could ever be a reviewer/approver
     * of anything"; this re-check enforces the actual data-driven role for this document's
     * category (or the default rule when no category-specific override exists). */
    private void assertRoleForStage(Document document, ReviewStage stage, String actorUsername) {
        String requiredRole = roleFor(document, stage);
        User actor = currentUser(actorUsername);
        boolean hasRole = actor.getRoles().stream().anyMatch(r -> r.getCode().equals(requiredRole));
        if (!hasRole) {
            throw new DocumentAccessDeniedException(document.getId());
        }
    }

    private String reviewerRoleFor(Document document) {
        return roleFor(document, ReviewStage.REVIEW);
    }

    private String approverRoleFor(Document document) {
        return roleFor(document, ReviewStage.APPROVAL);
    }

    private String roleFor(Document document, ReviewStage stage) {
        return documentWorkflowRoleRepository.findByStage(stage).stream()
                .filter(r -> r.getCategory() != null && r.getCategory().equals(document.getCategory()))
                .findFirst()
                .or(() -> documentWorkflowRoleRepository.findByStage(stage).stream()
                        .filter(r -> r.getCategory() == null)
                        .findFirst())
                .map(r -> r.getRoleCode())
                .orElseThrow(() -> new IllegalStateException("No workflow role configured for stage " + stage));
    }

    private void notifyRoleHolders(String roleCode, DocumentVersion version, String message) {
        for (User recipient : userRepository.findByRoles_Code(roleCode)) {
            notificationService.notify(
                    recipient.getId(),
                    "DOCUMENT_REVIEW",
                    "Document version " + message,
                    version.getDocument().getTitle() + " v" + version.getVersionNumber() + " " + message,
                    "/documents/" + version.getDocument().getId());
        }
    }

    private DocumentReview newReview(
            DocumentVersion version, ReviewStage stage, ReviewAction action, String comment, User actor, ESignature signature) {
        DocumentReview review = new DocumentReview();
        review.setDocumentVersion(version);
        review.setStage(stage);
        review.setAction(action);
        review.setComment(comment);
        review.setActedBy(actor);
        review.setEsignature(signature);
        return review;
    }

    private DocumentVersion findVersion(Long documentId, int versionNumber) {
        return documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, versionNumber));
    }

    private ReviewAction parseAction(String value) {
        try {
            return ReviewAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidDocumentTransitionException("Unknown action: " + value);
        }
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
