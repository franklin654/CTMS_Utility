package com.ctms.ctms_backend.testresult.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.testresult.dto.CreateTestResultRequest;
import com.ctms.ctms_backend.testresult.dto.TestResultResponse;
import com.ctms.ctms_backend.testresult.entity.TestResult;
import com.ctms.ctms_backend.testresult.entity.TestResultStatus;
import com.ctms.ctms_backend.testresult.exception.InvalidTestResultTransitionException;
import com.ctms.ctms_backend.testresult.exception.TestResultNotFoundException;
import com.ctms.ctms_backend.testresult.exception.VisitSubjectMismatchException;
import com.ctms.ctms_backend.testresult.repository.TestResultRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.exception.VisitNotFoundException;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL-06 (not part of backlog v0.2, reintroduced per Phase 7 scope decision). `status`
 * (RECORDED/REVIEWED) is deliberately independent of `abnormal` -- see {@link TestResult}'s
 * javadoc for the reasoning. */
@Service
public class TestResultService {

    private final TestResultRepository testResultRepository;
    private final SubjectRepository subjectRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TestResultService(
            TestResultRepository testResultRepository,
            SubjectRepository subjectRepository,
            VisitRepository visitRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.testResultRepository = testResultRepository;
        this.subjectRepository = subjectRepository;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TestResultResponse record(CreateTestResultRequest req, String actorUsername) {
        Subject subject = subjectRepository.findById(req.subjectId())
                .orElseThrow(() -> new SubjectNotFoundException(req.subjectId()));
        Visit visit = visitRepository.findById(req.visitId())
                .orElseThrow(() -> new VisitNotFoundException(req.visitId()));
        if (!visit.getSubject().getId().equals(subject.getId())) {
            throw new VisitSubjectMismatchException(visit.getId(), subject.getId());
        }
        User actor = currentUser(actorUsername);

        TestResult result = new TestResult();
        result.setSubject(subject);
        result.setVisit(visit);
        result.setTestName(req.testName());
        result.setResultValue(req.resultValue());
        result.setUnits(req.units());
        result.setReferenceRange(req.referenceRange());
        result.setAbnormal(req.abnormal());
        result.setNotes(req.notes());
        result.setStatus(TestResultStatus.RECORDED);
        result.setCreatedBy(actor);
        result.setModifiedBy(actor);
        result = testResultRepository.save(result);

        auditService.record(
                "TestResult", String.valueOf(result.getId()), AuditAction.CREATE,
                null, req.testName() + " = " + req.resultValue() + " (subject " + subject.getSubjectCode() + ")", null);

        return TestResultResponse.from(result);
    }

    @Transactional
    public TestResultResponse review(Long id, String actorUsername) {
        TestResult result = findTestResult(id);
        if (result.getStatus() != TestResultStatus.RECORDED) {
            throw new InvalidTestResultTransitionException("Cannot review a test result from status " + result.getStatus());
        }
        User actor = currentUser(actorUsername);

        result.setStatus(TestResultStatus.REVIEWED);
        result.setReviewedBy(actor);
        result.setReviewedAt(Instant.now());
        result.setModifiedBy(actor);
        result = testResultRepository.save(result);

        auditService.record(
                "TestResult", String.valueOf(id), AuditAction.STATE_CHANGE,
                TestResultStatus.RECORDED.name(), TestResultStatus.REVIEWED.name(), null);

        return TestResultResponse.from(result);
    }

    @Transactional(readOnly = true)
    public List<TestResultResponse> list(Long subjectId) {
        return testResultRepository.findBySubjectIdOrderByCreatedAtDesc(subjectId).stream()
                .map(TestResultResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestResultResponse get(Long id) {
        return TestResultResponse.from(findTestResult(id));
    }

    @Transactional(readOnly = true)
    public TestResult findTestResult(Long id) {
        return testResultRepository.findById(id).orElseThrow(() -> new TestResultNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
