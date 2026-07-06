package com.ctms.ctms_backend.subject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.TransitionSubjectRequest;
import com.ctms.ctms_backend.subject.dto.WithdrawSubjectRequest;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectStatus;
import com.ctms.ctms_backend.subject.entity.SubjectStatusHistory;
import com.ctms.ctms_backend.subject.exception.InvalidSubjectTransitionException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.subject.repository.SubjectStatusHistoryRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SubjectLifecycleServiceTest {

    @Mock private SubjectRepository subjectRepository;
    @Mock private SubjectStatusHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private ESignatureService eSignatureService;

    @InjectMocks
    private SubjectLifecycleService lifecycleService;

    private User actor;
    private Subject subject;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));

        Study study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");

        Site site = new Site();
        site.setId(20L);
        site.setSiteCode("SITE-001");

        subject = new Subject();
        subject.setId(1000L);
        subject.setSubjectCode("SUBJ-001000");
        subject.setStudy(study);
        subject.setSite(site);
        subject.setStatus(SubjectStatus.SCREENED);
        subject.setCreatedBy(actor);
        subject.setModifiedBy(actor);
        lenient().when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));
        lenient().when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(historyRepository.save(any(SubjectStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("coordinator1", null, List.of(new SimpleGrantedAuthority("ROLE_SITE_COORDINATOR"))));

        lenient().when(eSignatureService.sign(
                        org.mockito.ArgumentMatchers.eq("coordinator1"), org.mockito.ArgumentMatchers.eq("correct-password"),
                        org.mockito.ArgumentMatchers.eq("Subject"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ESignature(actor, "Subject", "1000", "withdrawal"));
    }

    @Test
    void transition_screenedToEnrolled_succeeds() {
        SubjectResponse response = lifecycleService.transition(
                1000L, new TransitionSubjectRequest("ENROLLED", "consent signed"), "coordinator1");
        assertEquals("ENROLLED", response.status());
    }

    @Test
    void transition_skipStage_throws() {
        assertThrows(InvalidSubjectTransitionException.class, () -> lifecycleService.transition(
                1000L, new TransitionSubjectRequest("IN_TREATMENT", "skip"), "coordinator1"));
    }

    @Test
    void transition_targetWithdrawn_rejectedMustUseWithdrawEndpoint() {
        assertThrows(InvalidSubjectTransitionException.class, () -> lifecycleService.transition(
                1000L, new TransitionSubjectRequest("WITHDRAWN", "leaving"), "coordinator1"));
    }

    @Test
    void withdraw_fromScreened_succeeds() {
        SubjectResponse response = lifecycleService.withdraw(
                1000L, new WithdrawSubjectRequest("subject requested withdrawal", "correct-password"), "coordinator1");
        assertEquals("WITHDRAWN", response.status());
    }

    @Test
    void withdraw_fromEnrolled_succeeds() {
        subject.setStatus(SubjectStatus.ENROLLED);
        SubjectResponse response = lifecycleService.withdraw(
                1000L, new WithdrawSubjectRequest("lost to follow-up", "correct-password"), "coordinator1");
        assertEquals("WITHDRAWN", response.status());
    }

    @Test
    void withdraw_fromInTreatment_succeeds() {
        subject.setStatus(SubjectStatus.IN_TREATMENT);
        SubjectResponse response = lifecycleService.withdraw(
                1000L, new WithdrawSubjectRequest("adverse reaction", "correct-password"), "coordinator1");
        assertEquals("WITHDRAWN", response.status());
    }

    @Test
    void withdraw_fromCompleted_throws() {
        subject.setStatus(SubjectStatus.COMPLETED);
        assertThrows(InvalidSubjectTransitionException.class, () -> lifecycleService.withdraw(
                1000L, new WithdrawSubjectRequest("too late", "correct-password"), "coordinator1"));
    }

    @Test
    void withdraw_alreadyWithdrawn_throws() {
        subject.setStatus(SubjectStatus.WITHDRAWN);
        assertThrows(InvalidSubjectTransitionException.class, () -> lifecycleService.withdraw(
                1000L, new WithdrawSubjectRequest("again", "correct-password"), "coordinator1"));
    }

    @Test
    void withdraw_wrongPassword_throwsAndLeavesStatusUntouched() {
        when(eSignatureService.sign(
                        "coordinator1", "wrong-password", "Subject", "1000", "leaving"))
                .thenThrow(new com.ctms.ctms_backend.security.exception.InvalidCredentialsException());

        assertThrows(com.ctms.ctms_backend.security.exception.InvalidCredentialsException.class, () -> lifecycleService.withdraw(
                1000L, new WithdrawSubjectRequest("leaving", "wrong-password"), "coordinator1"));
        assertEquals(SubjectStatus.SCREENED, subject.getStatus());
    }
}
