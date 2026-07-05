package com.ctms.ctms_backend.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.Document;
import com.ctms.ctms_backend.document.DocumentService;
import com.ctms.ctms_backend.document.DocumentVersion;
import com.ctms.ctms_backend.document.DocumentVersionRepository;
import com.ctms.ctms_backend.document.dto.FinalApprovalRequest;
import com.ctms.ctms_backend.document.dto.ReviewDecisionRequest;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.entity.DocumentWorkflowRole;
import com.ctms.ctms_backend.document.entity.ReviewStage;
import com.ctms.ctms_backend.document.exception.DocumentAccessDeniedException;
import com.ctms.ctms_backend.document.exception.InvalidDocumentTransitionException;
import com.ctms.ctms_backend.document.repository.DocumentReviewRepository;
import com.ctms.ctms_backend.document.repository.DocumentWorkflowRoleRepository;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowServiceTest {

    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private DocumentReviewRepository documentReviewRepository;
    @Mock private DocumentWorkflowRoleRepository documentWorkflowRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ESignatureService eSignatureService;
    @Mock private NotificationService notificationService;
    @Mock private DocumentService documentService;

    @InjectMocks
    private DocumentWorkflowService workflowService;

    private User reviewer;
    private User approver;
    private Document document;
    private DocumentVersion version;

    @BeforeEach
    void setUp() {
        Role studyManagerRole = new Role();
        studyManagerRole.setCode(Role.STUDY_MANAGER);
        reviewer = new User();
        reviewer.setId(1L);
        reviewer.setUsername("reviewer1");
        reviewer.setRoles(new HashSet<>(List.of(studyManagerRole)));

        Role qaRole = new Role();
        qaRole.setCode(Role.QA_COMPLIANCE_AUDITOR);
        approver = new User();
        approver.setId(2L);
        approver.setUsername("approver1");
        approver.setRoles(new HashSet<>(List.of(qaRole)));

        document = new Document();
        document.setId(10L);
        document.setTitle("Protocol");
        document.setCategory("PROTOCOL");

        version = new DocumentVersion();
        version.setId(100L);
        version.setDocument(document);
        version.setVersionNumber(2);
        version.setStatus(DocumentVersionStatus.DRAFT);

        lenient().when(userRepository.findByUsername("reviewer1")).thenReturn(Optional.of(reviewer));
        lenient().when(userRepository.findByUsername("approver1")).thenReturn(Optional.of(approver));

        DocumentWorkflowRole reviewRole = new DocumentWorkflowRole();
        reviewRole.setCategory(null);
        reviewRole.setStage(ReviewStage.REVIEW);
        reviewRole.setRoleCode(Role.STUDY_MANAGER);
        lenient().when(documentWorkflowRoleRepository.findByStage(ReviewStage.REVIEW)).thenReturn(List.of(reviewRole));

        DocumentWorkflowRole approvalRole = new DocumentWorkflowRole();
        approvalRole.setCategory(null);
        approvalRole.setStage(ReviewStage.APPROVAL);
        approvalRole.setRoleCode(Role.QA_COMPLIANCE_AUDITOR);
        lenient().when(documentWorkflowRoleRepository.findByStage(ReviewStage.APPROVAL)).thenReturn(List.of(approvalRole));

        lenient().when(documentVersionRepository.findByDocumentIdAndVersionNumber(10L, 2)).thenReturn(Optional.of(version));
        lenient().when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(documentReviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByRoles_Code(anyString())).thenReturn(List.of());
    }

    @Test
    void submitForReview_fromDraft_succeeds() {
        var response = workflowService.submitForReview(10L, 2, "reviewer1");
        assertEquals("SUBMITTED", response.action());
        assertEquals(DocumentVersionStatus.PENDING_REVIEW, version.getStatus());
    }

    @Test
    void submitForReview_fromNonDraft_throwsInvalidTransition() {
        version.setStatus(DocumentVersionStatus.CURRENT);
        assertThrows(InvalidDocumentTransitionException.class,
                () -> workflowService.submitForReview(10L, 2, "reviewer1"));
    }

    @Test
    void reviewerDecide_approve_movesToPendingApproval() {
        version.setStatus(DocumentVersionStatus.PENDING_REVIEW);
        var response = workflowService.reviewerDecide(
                10L, 2, new ReviewDecisionRequest("APPROVED", null), "reviewer1");
        assertEquals("APPROVED", response.action());
        assertEquals(DocumentVersionStatus.PENDING_APPROVAL, version.getStatus());
    }

    @Test
    void reviewerDecide_rejectWithoutComment_throws() {
        version.setStatus(DocumentVersionStatus.PENDING_REVIEW);
        assertThrows(InvalidDocumentTransitionException.class, () -> workflowService.reviewerDecide(
                10L, 2, new ReviewDecisionRequest("REJECTED", null), "reviewer1"));
    }

    @Test
    void reviewerDecide_rejectWithComment_succeeds() {
        version.setStatus(DocumentVersionStatus.PENDING_REVIEW);
        var response = workflowService.reviewerDecide(
                10L, 2, new ReviewDecisionRequest("REJECTED", "Missing signature page"), "reviewer1");
        assertEquals("REJECTED", response.action());
        assertEquals(DocumentVersionStatus.REJECTED, version.getStatus());
    }

    @Test
    void reviewerDecide_changesRequested_movesToDraft() {
        version.setStatus(DocumentVersionStatus.PENDING_REVIEW);
        var response = workflowService.reviewerDecide(
                10L, 2, new ReviewDecisionRequest("CHANGES_REQUESTED", "Please fix formatting"), "reviewer1");
        assertEquals("CHANGES_REQUESTED", response.action());
        assertEquals(DocumentVersionStatus.DRAFT, version.getStatus());
    }

    @Test
    void reviewerDecide_wrongRole_throwsAccessDenied() {
        version.setStatus(DocumentVersionStatus.PENDING_REVIEW);
        assertThrows(DocumentAccessDeniedException.class, () -> workflowService.reviewerDecide(
                10L, 2, new ReviewDecisionRequest("APPROVED", null), "approver1"));
    }

    @Test
    void approverFinalDecide_approve_signsAndPromotes() {
        version.setStatus(DocumentVersionStatus.PENDING_APPROVAL);
        ESignature signature = new ESignature(approver, "DocumentVersion", "100", "sign-off");
        when(eSignatureService.sign("approver1", "correct-pw", "DocumentVersion", "100", "sign-off"))
                .thenReturn(signature);

        var response = workflowService.approverFinalDecide(
                10L, 2, new FinalApprovalRequest("APPROVED", null, "correct-pw", "sign-off"), "approver1");

        assertEquals("APPROVED", response.action());
        assertEquals(DocumentVersionStatus.CURRENT, version.getStatus());
    }

    @Test
    void approverFinalDecide_wrongPassword_propagatesInvalidCredentials() {
        version.setStatus(DocumentVersionStatus.PENDING_APPROVAL);
        when(eSignatureService.sign("approver1", "wrong", "DocumentVersion", "100", "sign-off"))
                .thenThrow(new InvalidCredentialsException());

        assertThrows(InvalidCredentialsException.class, () -> workflowService.approverFinalDecide(
                10L, 2, new FinalApprovalRequest("APPROVED", null, "wrong", "sign-off"), "approver1"));
    }

    @Test
    void approverFinalDecide_rejectWithoutComment_throws() {
        version.setStatus(DocumentVersionStatus.PENDING_APPROVAL);
        assertThrows(InvalidDocumentTransitionException.class, () -> workflowService.approverFinalDecide(
                10L, 2, new FinalApprovalRequest("REJECTED", null, null, null), "approver1"));
    }

    @Test
    void approverFinalDecide_fromWrongStatus_throwsInvalidTransition() {
        version.setStatus(DocumentVersionStatus.DRAFT);
        assertThrows(InvalidDocumentTransitionException.class, () -> workflowService.approverFinalDecide(
                10L, 2, new FinalApprovalRequest("APPROVED", null, "pw", "reason"), "approver1"));
    }

    @Test
    void approverFinalDecide_wrongRole_throwsAccessDenied() {
        version.setStatus(DocumentVersionStatus.PENDING_APPROVAL);
        assertThrows(DocumentAccessDeniedException.class, () -> workflowService.approverFinalDecide(
                10L, 2, new FinalApprovalRequest("APPROVED", null, "pw", "reason"), "reviewer1"));
    }
}
