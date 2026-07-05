package com.ctms.ctms_backend.subject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.rules.RuleSetService;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.dto.EligibilityAnswerRequest;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.entity.CriterionType;
import com.ctms.ctms_backend.subject.entity.EligibilityCriterion;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectEligibilityAnswer;
import com.ctms.ctms_backend.subject.exception.EligibilityFailedException;
import com.ctms.ctms_backend.subject.exception.IncompleteEligibilityAnswersException;
import com.ctms.ctms_backend.subject.exception.StudySiteMismatchException;
import com.ctms.ctms_backend.subject.repository.EligibilityCriterionRepository;
import com.ctms.ctms_backend.subject.repository.SubjectEligibilityAnswerRepository;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.service.VisitSchedulingService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock private SubjectRepository subjectRepository;
    @Mock private EligibilityCriterionRepository criterionRepository;
    @Mock private SubjectEligibilityAnswerRepository answerRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private RuleSetService ruleSetService;
    @Mock private VisitSchedulingService visitSchedulingService;
    @Mock private TaskService taskService;

    @InjectMocks
    private SubjectService subjectService;

    private User creator;
    private Study study;
    private Site site;
    private EligibilityCriterion inclusionCriterion;
    private EligibilityCriterion exclusionCriterion;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setUsername("coordinator1");
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(creator));

        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        study.setCreatedBy(creator);
        lenient().when(studyRepository.findById(10L)).thenReturn(Optional.of(study));

        site = new Site();
        site.setId(20L);
        site.setSiteCode("SITE-001");
        site.setStudy(study);
        lenient().when(siteRepository.findById(20L)).thenReturn(Optional.of(site));

        inclusionCriterion = new EligibilityCriterion();
        inclusionCriterion.setId(100L);
        inclusionCriterion.setStudy(study);
        inclusionCriterion.setLabel("Age >= 18");
        inclusionCriterion.setCriterionType(CriterionType.INCLUSION);
        inclusionCriterion.setActive(true);

        exclusionCriterion = new EligibilityCriterion();
        exclusionCriterion.setId(101L);
        exclusionCriterion.setStudy(study);
        exclusionCriterion.setLabel("Pregnant");
        exclusionCriterion.setCriterionType(CriterionType.EXCLUSION);
        exclusionCriterion.setActive(true);

        lenient().when(criterionRepository.findByStudyIdAndActiveTrue(10L))
                .thenReturn(List.of(inclusionCriterion, exclusionCriterion));

        lenient().when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
            Subject s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(1000L);
            }
            return s;
        });
        lenient().when(answerRepository.save(any(SubjectEligibilityAnswer.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("coordinator1", null, List.of(new SimpleGrantedAuthority("ROLE_SITE_COORDINATOR"))));
    }

    private EnrollSubjectRequest validRequest(boolean ageMet, boolean pregnant) {
        return new EnrollSubjectRequest(
                10L, 20L, "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", "555-1234", "jane@example.com",
                "1 Main St", "John Doe", "some note", "some medical history", LocalDate.now(),
                List.of(new EligibilityAnswerRequest(100L, ageMet), new EligibilityAnswerRequest(101L, pregnant)));
    }

    @Test
    void enrollSubject_studySiteMismatch_throws() {
        Study otherStudy = new Study();
        otherStudy.setId(99L);
        site.setStudy(otherStudy);

        assertThrows(StudySiteMismatchException.class,
                () -> subjectService.enrollSubject(validRequest(true, false), "coordinator1"));
    }

    @Test
    void enrollSubject_missingAnswer_throwsIncomplete() {
        EnrollSubjectRequest req = new EnrollSubjectRequest(
                10L, 20L, "Jane", "Doe", LocalDate.of(1990, 1, 1), "FEMALE", null, null, null, null, null, null,
                LocalDate.now(), List.of(new EligibilityAnswerRequest(100L, true)));

        assertThrows(IncompleteEligibilityAnswersException.class,
                () -> subjectService.enrollSubject(req, "coordinator1"));
    }

    @Test
    void enrollSubject_eligibilityFails_blocksCreation() {
        when(ruleSetService.evaluate(eq("ELIGIBILITY_DEFAULT"), anyList()))
                .thenReturn(List.of("Inclusion criterion not met: Age >= 18"));

        assertThrows(EligibilityFailedException.class,
                () -> subjectService.enrollSubject(validRequest(false, false), "coordinator1"));
    }

    @Test
    void enrollSubject_happyPath_generatesCodeAndScreenedStatus() {
        when(ruleSetService.evaluate(eq("ELIGIBILITY_DEFAULT"), anyList())).thenReturn(List.of());

        SubjectResponse response = subjectService.enrollSubject(validRequest(true, false), "coordinator1");
        assertEquals("SCREENED", response.status());
        assertEquals("SUBJ-001000", response.subjectCode());
    }

    @Test
    void get_medicalHistoryHiddenFromCraMonitor() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("cra1", null, List.of(new SimpleGrantedAuthority("ROLE_" + Role.CRA_MONITOR))));

        Subject subject = new Subject();
        subject.setId(1000L);
        subject.setStudy(study);
        subject.setSite(site);
        subject.setFirstName("Jane");
        subject.setLastName("Doe");
        subject.setDateOfBirth(LocalDate.of(1990, 1, 1));
        subject.setScreeningDate(LocalDate.now());
        subject.setMedicalHistory("sensitive info");
        subject.setCreatedBy(creator);
        subject.setModifiedBy(creator);
        when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));

        SubjectResponse response = subjectService.get(1000L);
        assertNull(response.medicalHistory());
    }

    @Test
    void get_medicalHistoryVisibleToStudyManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("sm1", null, List.of(new SimpleGrantedAuthority("ROLE_" + Role.STUDY_MANAGER))));

        Subject subject = new Subject();
        subject.setId(1000L);
        subject.setStudy(study);
        subject.setSite(site);
        subject.setFirstName("Jane");
        subject.setLastName("Doe");
        subject.setDateOfBirth(LocalDate.of(1990, 1, 1));
        subject.setScreeningDate(LocalDate.now());
        subject.setMedicalHistory("sensitive info");
        subject.setCreatedBy(creator);
        subject.setModifiedBy(creator);
        when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));

        SubjectResponse response = subjectService.get(1000L);
        assertEquals("sensitive info", response.medicalHistory());
    }
}
