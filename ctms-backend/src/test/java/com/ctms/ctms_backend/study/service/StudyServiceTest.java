package com.ctms.ctms_backend.study.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.dto.CloseoutStudyRequest;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.dto.TransitionStudyRequest;
import com.ctms.ctms_backend.study.dto.UpdateStudyRequest;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.entity.StudyStatus;
import com.ctms.ctms_backend.study.exception.DuplicateProtocolIdException;
import com.ctms.ctms_backend.study.exception.InvalidStudyTransitionException;
import com.ctms.ctms_backend.study.exception.StudyClosedException;
import com.ctms.ctms_backend.study.exception.StudyFieldLockedException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.study.repository.StudyStatusHistoryRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Covers the state-machine and lock/unlock business-rule branches, not just the happy path. */
@ExtendWith(MockitoExtension.class)
class StudyServiceTest {

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private StudyStatusHistoryRepository historyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ESignatureService eSignatureService;

    @InjectMocks
    private StudyService studyService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setUsername("study.manager");
        lenient().when(userRepository.findByUsername("study.manager")).thenReturn(Optional.of(creator));

        // Mimic DB auto-increment: assign an id on first save, matching the two-step studyCode pattern.
        lenient().when(studyRepository.save(any(Study.class))).thenAnswer(invocation -> {
            Study s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(1L);
            }
            return s;
        });
    }

    private CreateStudyRequest validCreateRequest() {
        return new CreateStudyRequest(
                "Phase III Trial", "PROTO-1", "1.0", "PHASE_III", "Acme Pharma",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "desc");
    }

    @Test
    void createStudy_duplicateProtocolId_throws() {
        when(studyRepository.existsByProtocolId("PROTO-1")).thenReturn(true);
        assertThrows(DuplicateProtocolIdException.class,
                () -> studyService.createStudy(validCreateRequest(), "study.manager"));
    }

    @Test
    void createStudy_happyPath_generatesCodeAndDraftStatus() {
        when(studyRepository.existsByProtocolId("PROTO-1")).thenReturn(false);
        StudyResponse response = studyService.createStudy(validCreateRequest(), "study.manager");
        assertEquals("DRAFT", response.status());
        assertEquals("ST-000001", response.studyCode());
    }

    private Study draftStudy() {
        Study s = new Study();
        s.setId(1L);
        s.setStudyCode("ST-000001");
        s.setName("Phase III Trial");
        s.setProtocolId("PROTO-1");
        s.setProtocolVersion("1.0");
        s.setPhase("PHASE_III");
        s.setSponsor("Acme Pharma");
        s.setStatus(StudyStatus.DRAFT);
        s.setCreatedBy(creator);
        s.setModifiedBy(creator);
        return s;
    }

    private UpdateStudyRequest updateRequest(String protocolId) {
        return new UpdateStudyRequest("New Name", protocolId, "1.1", "PHASE_III", "Acme Pharma", null, null, null, null, null);
    }

    @Test
    void updateStudy_protocolIdChangeWhileDraft_allowed() {
        Study study = draftStudy();
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));
        when(studyRepository.existsByProtocolId("PROTO-2")).thenReturn(false);

        StudyResponse response = studyService.updateStudy(1L, updateRequest("PROTO-2"), "study.manager");
        assertEquals("PROTO-2", response.protocolId());
    }

    @Test
    void updateStudy_protocolIdChangeAfterDraft_throwsFieldLocked() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.ACTIVE);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        assertThrows(StudyFieldLockedException.class,
                () -> studyService.updateStudy(1L, updateRequest("CHANGED"), "study.manager"));
    }

    @Test
    void updateStudy_afterCloseout_throwsClosed() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.CLOSEOUT);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        assertThrows(StudyClosedException.class,
                () -> studyService.updateStudy(1L, updateRequest("PROTO-1"), "study.manager"));
    }

    @Test
    void transition_draftToActive_succeeds() {
        Study study = draftStudy();
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        StudyResponse response = studyService.transition(
                1L, new TransitionStudyRequest("ACTIVE", "IRB approval received"), "study.manager");
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void transition_skipStage_throwsInvalidTransition() {
        Study study = draftStudy(); // DRAFT -> CONDUCT skips ACTIVE
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        assertThrows(InvalidStudyTransitionException.class,
                () -> studyService.transition(1L, new TransitionStudyRequest("CONDUCT", "skip"), "study.manager"));
    }

    @Test
    void transition_backward_throwsInvalidTransition() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.ACTIVE);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        assertThrows(InvalidStudyTransitionException.class,
                () -> studyService.transition(1L, new TransitionStudyRequest("DRAFT", "revert"), "study.manager"));
    }

    @Test
    void transition_targetCloseout_rejectedMustUseCloseoutEndpoint() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.CONDUCT);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        assertThrows(InvalidStudyTransitionException.class,
                () -> studyService.transition(1L, new TransitionStudyRequest("CLOSEOUT", "done"), "study.manager"));
    }

    @Test
    void closeout_fromNonConduct_throwsInvalidTransition() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.ACTIVE);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        assertThrows(InvalidStudyTransitionException.class,
                () -> studyService.closeout(1L, new CloseoutStudyRequest("pw", "reason"), "study.manager"));
    }

    @Test
    void closeout_wrongPassword_propagatesInvalidCredentials() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.CONDUCT);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));
        when(eSignatureService.sign("study.manager", "wrong", "Study", "1", "reason"))
                .thenThrow(new InvalidCredentialsException());

        assertThrows(InvalidCredentialsException.class,
                () -> studyService.closeout(1L, new CloseoutStudyRequest("wrong", "reason"), "study.manager"));
    }

    @Test
    void closeout_success_setsClosedStatus() {
        Study study = draftStudy();
        study.setStatus(StudyStatus.CONDUCT);
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));
        ESignature signature = new ESignature(creator, "Study", "1", "reason");
        when(eSignatureService.sign("study.manager", "correct", "Study", "1", "reason")).thenReturn(signature);

        StudyResponse response = studyService.closeout(1L, new CloseoutStudyRequest("correct", "reason"), "study.manager");
        assertEquals("CLOSEOUT", response.status());
    }
}
