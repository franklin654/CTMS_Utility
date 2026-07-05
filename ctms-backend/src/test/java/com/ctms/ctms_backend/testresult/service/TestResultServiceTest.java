package com.ctms.ctms_backend.testresult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.testresult.dto.CreateTestResultRequest;
import com.ctms.ctms_backend.testresult.dto.TestResultResponse;
import com.ctms.ctms_backend.testresult.entity.TestResult;
import com.ctms.ctms_backend.testresult.entity.TestResultStatus;
import com.ctms.ctms_backend.testresult.exception.InvalidTestResultTransitionException;
import com.ctms.ctms_backend.testresult.exception.VisitSubjectMismatchException;
import com.ctms.ctms_backend.testresult.repository.TestResultRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestResultServiceTest {

    @Mock private TestResultRepository testResultRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private TestResultService testResultService;

    private Subject subject;
    private Visit visit;
    private User actor;
    private TestResult recordedResult;

    @BeforeEach
    void setUp() {
        subject = new Subject();
        subject.setId(1000L);
        subject.setSubjectCode("SUBJ-001000");

        visit = new Visit();
        visit.setId(5L);
        visit.setSubject(subject);
        visit.setName("Screening Visit");

        actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");

        lenient().when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));
        lenient().when(visitRepository.findById(5L)).thenReturn(Optional.of(visit));
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));
        lenient().when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(100L);
            }
            return r;
        });

        recordedResult = new TestResult();
        recordedResult.setId(100L);
        recordedResult.setSubject(subject);
        recordedResult.setVisit(visit);
        recordedResult.setTestName("Hemoglobin");
        recordedResult.setResultValue("13.5");
        recordedResult.setStatus(TestResultStatus.RECORDED);
        recordedResult.setCreatedBy(actor);
        lenient().when(testResultRepository.findById(100L)).thenReturn(Optional.of(recordedResult));
    }

    @Test
    void record_happyPath_savesAndAudits() {
        CreateTestResultRequest req = new CreateTestResultRequest(1000L, 5L, "Hemoglobin", "13.5", "g/dL", "12-16", false, null);
        TestResultResponse response = testResultService.record(req, "coordinator1");

        assertEquals("Hemoglobin", response.testName());
        assertEquals("RECORDED", response.status());
        assertEquals(false, response.abnormal());
    }

    @Test
    void record_visitBelongsToDifferentSubject_throws() {
        Subject otherSubject = new Subject();
        otherSubject.setId(2000L);
        visit.setSubject(otherSubject);

        CreateTestResultRequest req = new CreateTestResultRequest(1000L, 5L, "Hemoglobin", "13.5", null, null, false, null);
        assertThrows(VisitSubjectMismatchException.class, () -> testResultService.record(req, "coordinator1"));
    }

    @Test
    void review_transitionsRecordedToReviewed() {
        TestResultResponse response = testResultService.review(100L, "coordinator1");
        assertEquals("REVIEWED", response.status());
        assertEquals("coordinator1", response.reviewedByUsername());
    }

    @Test
    void review_alreadyReviewed_throws() {
        recordedResult.setStatus(TestResultStatus.REVIEWED);
        assertThrows(InvalidTestResultTransitionException.class, () -> testResultService.review(100L, "coordinator1"));
    }
}
