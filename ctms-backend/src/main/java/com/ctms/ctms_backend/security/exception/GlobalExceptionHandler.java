package com.ctms.ctms_backend.security.exception;

import com.ctms.ctms_backend.adverseevent.exception.AdverseEventNotFoundException;
import com.ctms.ctms_backend.adverseevent.exception.InvalidAdverseEventTransitionException;
import com.ctms.ctms_backend.budget.exception.BudgetNotFoundException;
import com.ctms.ctms_backend.budget.exception.BudgetVersionNotFoundException;
import com.ctms.ctms_backend.budget.exception.DuplicateBudgetException;
import com.ctms.ctms_backend.budget.exception.MissingBudgetVersionReasonException;
import com.ctms.ctms_backend.deviation.exception.InvalidProtocolDeviationException;
import com.ctms.ctms_backend.document.exception.DocumentAccessDeniedException;
import com.ctms.ctms_backend.document.exception.DocumentLockedException;
import com.ctms.ctms_backend.document.exception.DocumentNotFoundException;
import com.ctms.ctms_backend.document.exception.DocumentVersionNotFoundException;
import com.ctms.ctms_backend.document.exception.DocumentRequirementNotFoundException;
import com.ctms.ctms_backend.document.exception.InvalidDocumentTransitionException;
import com.ctms.ctms_backend.document.exception.MissingConsentException;
import com.ctms.ctms_backend.document.exception.MissingMandatoryDocumentsException;
import com.ctms.ctms_backend.milestone.exception.DuplicateMilestoneTypeException;
import com.ctms.ctms_backend.milestone.exception.InvalidMilestoneActualDateException;
import com.ctms.ctms_backend.milestone.exception.MilestoneNotFoundException;
import com.ctms.ctms_backend.monitoring.exception.MonitoringVisitNotFoundException;
import com.ctms.ctms_backend.monitoring.exception.MonitoringVisitReportNotFoundException;
import com.ctms.ctms_backend.patientportal.exception.NoLinkedSubjectException;
import com.ctms.ctms_backend.payment.exception.InvalidPaymentTransitionException;
import com.ctms.ctms_backend.payment.exception.PaymentNotFoundException;
import com.ctms.ctms_backend.rules.RuleCompilationException;
import com.ctms.ctms_backend.site.exception.ChecklistItemNotFoundException;
import com.ctms.ctms_backend.site.exception.DuplicateSiteCodeException;
import com.ctms.ctms_backend.site.exception.InvalidCraAssignmentException;
import com.ctms.ctms_backend.site.exception.InvalidSiteTransitionException;
import com.ctms.ctms_backend.site.exception.SiteActivationBlockedException;
import com.ctms.ctms_backend.site.exception.SiteNotFoundException;
import com.ctms.ctms_backend.study.exception.DuplicateProtocolIdException;
import com.ctms.ctms_backend.study.exception.InvalidStudyTransitionException;
import com.ctms.ctms_backend.study.exception.StudyClosedException;
import com.ctms.ctms_backend.study.exception.StudyFieldLockedException;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.subject.exception.EligibilityCriterionNotFoundException;
import com.ctms.ctms_backend.subject.exception.EligibilityFailedException;
import com.ctms.ctms_backend.subject.exception.IncompleteEligibilityAnswersException;
import com.ctms.ctms_backend.subject.exception.InvalidSubjectTransitionException;
import com.ctms.ctms_backend.subject.exception.NoPortalAccountException;
import com.ctms.ctms_backend.subject.exception.PortalAccountAlreadyExistsException;
import com.ctms.ctms_backend.subject.exception.StudySiteMismatchException;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.task.exception.InvalidTaskTransitionException;
import com.ctms.ctms_backend.task.exception.TaskNotFoundException;
import com.ctms.ctms_backend.testresult.exception.InvalidTestResultTransitionException;
import com.ctms.ctms_backend.testresult.exception.TestResultAttachmentNotFoundException;
import com.ctms.ctms_backend.testresult.exception.TestResultNotFoundException;
import com.ctms.ctms_backend.testresult.exception.VisitSubjectMismatchException;
import com.ctms.ctms_backend.visit.exception.CrossStudyDependencyException;
import com.ctms.ctms_backend.visit.exception.InvalidVisitTransitionException;
import com.ctms.ctms_backend.visit.exception.VisitDependencyNotMetException;
import com.ctms.ctms_backend.visit.exception.VisitNotFoundException;
import com.ctms.ctms_backend.visit.exception.VisitTemplateDependencyCycleException;
import com.ctms.ctms_backend.visit.exception.VisitTemplateNotFoundException;
import com.ctms.ctms_backend.visit.exception.VisitTemplateWindowInvalidException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException e) {
        return error(HttpStatus.LOCKED, e.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidToken(InvalidTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<Object> handlePasswordPolicyViolation(PasswordPolicyViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("timestamp", Instant.now(), "message", e.getMessage(), "violations", e.getViolations()));
    }

    @ExceptionHandler(RuleCompilationException.class)
    public ResponseEntity<Object> handleRuleCompilation(RuleCompilationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(StudyNotFoundException.class)
    public ResponseEntity<Object> handleStudyNotFound(StudyNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateProtocolIdException.class)
    public ResponseEntity<Object> handleDuplicateProtocolId(DuplicateProtocolIdException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({InvalidStudyTransitionException.class, StudyFieldLockedException.class, StudyClosedException.class})
    public ResponseEntity<Object> handleStudyStateViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({DocumentNotFoundException.class, DocumentVersionNotFoundException.class})
    public ResponseEntity<Object> handleDocumentNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidDocumentTransitionException.class, DocumentLockedException.class})
    public ResponseEntity<Object> handleDocumentStateViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DocumentAccessDeniedException.class)
    public ResponseEntity<Object> handleDocumentAccessDenied(DocumentAccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(SiteNotFoundException.class)
    public ResponseEntity<Object> handleSiteNotFound(SiteNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateSiteCodeException.class)
    public ResponseEntity<Object> handleDuplicateSiteCode(DuplicateSiteCodeException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({InvalidSiteTransitionException.class, ChecklistItemNotFoundException.class, InvalidCraAssignmentException.class})
    public ResponseEntity<Object> handleSiteStateViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SiteActivationBlockedException.class)
    public ResponseEntity<Object> handleSiteActivationBlocked(SiteActivationBlockedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("timestamp", Instant.now(), "message", e.getMessage(), "missingItems", e.getMissingItems()));
    }

    @ExceptionHandler({SubjectNotFoundException.class, EligibilityCriterionNotFoundException.class})
    public ResponseEntity<Object> handleSubjectNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NoLinkedSubjectException.class)
    public ResponseEntity<Object> handleNoLinkedSubject(NoLinkedSubjectException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({
        InvalidSubjectTransitionException.class,
        StudySiteMismatchException.class,
        IncompleteEligibilityAnswersException.class,
        PortalAccountAlreadyExistsException.class,
        NoPortalAccountException.class
    })
    public ResponseEntity<Object> handleSubjectStateViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(EligibilityFailedException.class)
    public ResponseEntity<Object> handleEligibilityFailed(EligibilityFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("timestamp", Instant.now(), "message", e.getMessage(), "violations", e.getViolations()));
    }

    @ExceptionHandler({VisitNotFoundException.class, VisitTemplateNotFoundException.class})
    public ResponseEntity<Object> handleVisitNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidVisitTransitionException.class, VisitTemplateWindowInvalidException.class})
    public ResponseEntity<Object> handleVisitStateViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Object> handleTaskNotFound(TaskNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidTaskTransitionException.class)
    public ResponseEntity<Object> handleTaskStateViolation(InvalidTaskTransitionException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({TestResultNotFoundException.class, TestResultAttachmentNotFoundException.class})
    public ResponseEntity<Object> handleTestResultNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidTestResultTransitionException.class, VisitSubjectMismatchException.class})
    public ResponseEntity<Object> handleTestResultStateViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AdverseEventNotFoundException.class)
    public ResponseEntity<Object> handleAdverseEventNotFound(AdverseEventNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidAdverseEventTransitionException.class)
    public ResponseEntity<Object> handleAdverseEventStateViolation(InvalidAdverseEventTransitionException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MonitoringVisitNotFoundException.class, MonitoringVisitReportNotFoundException.class})
    public ResponseEntity<Object> handleMonitoringVisitNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MilestoneNotFoundException.class)
    public ResponseEntity<Object> handleMilestoneNotFound(MilestoneNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateMilestoneTypeException.class)
    public ResponseEntity<Object> handleDuplicateMilestoneType(DuplicateMilestoneTypeException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidMilestoneActualDateException.class)
    public ResponseEntity<Object> handleInvalidMilestoneActualDate(InvalidMilestoneActualDateException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({BudgetNotFoundException.class, BudgetVersionNotFoundException.class})
    public ResponseEntity<Object> handleBudgetNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateBudgetException.class)
    public ResponseEntity<Object> handleDuplicateBudget(DuplicateBudgetException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MissingBudgetVersionReasonException.class)
    public ResponseEntity<Object> handleMissingBudgetVersionReason(MissingBudgetVersionReasonException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Object> handlePaymentNotFound(PaymentNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentTransitionException.class)
    public ResponseEntity<Object> handleInvalidPaymentTransition(InvalidPaymentTransitionException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({VisitTemplateDependencyCycleException.class, CrossStudyDependencyException.class, VisitDependencyNotMetException.class})
    public ResponseEntity<Object> handleVisitDependencyViolation(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DocumentRequirementNotFoundException.class)
    public ResponseEntity<Object> handleDocumentRequirementNotFound(DocumentRequirementNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MissingMandatoryDocumentsException.class)
    public ResponseEntity<Object> handleMissingMandatoryDocuments(MissingMandatoryDocumentsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("timestamp", Instant.now(), "message", e.getMessage(), "missingCategories", e.getMissingCategories()));
    }

    @ExceptionHandler(MissingConsentException.class)
    public ResponseEntity<Object> handleMissingConsent(MissingConsentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidProtocolDeviationException.class)
    public ResponseEntity<Object> handleInvalidProtocolDeviation(InvalidProtocolDeviationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed upload size");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed: " + e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "message", message));
    }
}
