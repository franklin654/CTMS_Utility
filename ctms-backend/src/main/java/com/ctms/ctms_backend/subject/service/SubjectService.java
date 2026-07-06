package com.ctms.ctms_backend.subject.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.rules.RuleSetService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.exception.SiteNotFoundException;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.dto.EligibilityAnswerRequest;
import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.UpdateOwnProfileRequest;
import com.ctms.ctms_backend.subject.dto.UpdateSubjectRequest;
import com.ctms.ctms_backend.subject.entity.EligibilityCriterion;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.entity.SubjectEligibilityAnswer;
import com.ctms.ctms_backend.subject.exception.EligibilityFailedException;
import com.ctms.ctms_backend.subject.exception.IncompleteEligibilityAnswersException;
import com.ctms.ctms_backend.subject.exception.StudySiteMismatchException;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.EligibilityCriterionRepository;
import com.ctms.ctms_backend.subject.repository.SubjectEligibilityAnswerRepository;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.subject.rules.EligibilityAnswerFact;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.service.VisitSchedulingService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

    private static final String ELIGIBILITY_RULE_SET = "ELIGIBILITY_DEFAULT";

    private final SubjectRepository subjectRepository;
    private final EligibilityCriterionRepository criterionRepository;
    private final SubjectEligibilityAnswerRepository answerRepository;
    private final StudyRepository studyRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final RuleSetService ruleSetService;
    private final VisitSchedulingService visitSchedulingService;
    private final TaskService taskService;

    public SubjectService(
            SubjectRepository subjectRepository,
            EligibilityCriterionRepository criterionRepository,
            SubjectEligibilityAnswerRepository answerRepository,
            StudyRepository studyRepository,
            SiteRepository siteRepository,
            UserRepository userRepository,
            AuditService auditService,
            RuleSetService ruleSetService,
            VisitSchedulingService visitSchedulingService,
            TaskService taskService) {
        this.subjectRepository = subjectRepository;
        this.criterionRepository = criterionRepository;
        this.answerRepository = answerRepository;
        this.studyRepository = studyRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.ruleSetService = ruleSetService;
        this.visitSchedulingService = visitSchedulingService;
        this.taskService = taskService;
    }

    @Transactional
    public SubjectResponse enrollSubject(EnrollSubjectRequest req, String creatorUsername) {
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        Site site = siteRepository.findById(req.siteId()).orElseThrow(() -> new SiteNotFoundException(req.siteId()));
        if (!site.getStudy().getId().equals(study.getId())) {
            throw new StudySiteMismatchException(study.getId(), site.getId());
        }

        List<EligibilityCriterion> criteria = criterionRepository.findByStudyIdAndActiveTrue(study.getId());
        Map<Long, EligibilityAnswerRequest> answersByCriterionId = req.eligibilityAnswers().stream()
                .collect(Collectors.toMap(EligibilityAnswerRequest::criterionId, a -> a, (a, b) -> a));

        for (EligibilityCriterion criterion : criteria) {
            if (!answersByCriterionId.containsKey(criterion.getId())) {
                throw new IncompleteEligibilityAnswersException(criterion.getLabel());
            }
        }

        List<Object> facts = criteria.stream()
                .map(criterion -> {
                    EligibilityAnswerRequest answer = answersByCriterionId.get(criterion.getId());
                    return (Object) new EligibilityAnswerFact(
                            criterion.getCriterionType().name(), criterion.getLabel(), answer.met());
                })
                .toList();

        @SuppressWarnings("unchecked")
        List<String> violations = (List<String>) (List<?>) ruleSetService.evaluate(ELIGIBILITY_RULE_SET, facts);
        if (!violations.isEmpty()) {
            throw new EligibilityFailedException(violations);
        }

        User creator = currentUser(creatorUsername);

        Subject subject = new Subject();
        subject.setStudy(study);
        subject.setSite(site);
        subject.setFirstName(req.firstName());
        subject.setLastName(req.lastName());
        subject.setDateOfBirth(req.dateOfBirth());
        subject.setGender(req.gender());
        subject.setContactPhone(req.contactPhone());
        subject.setContactEmail(req.contactEmail());
        subject.setAddress(req.address());
        subject.setEmergencyContact(req.emergencyContact());
        subject.setNotes(req.notes());
        subject.setMedicalHistory(req.medicalHistory());
        subject.setScreeningDate(req.screeningDate());
        subject.setCreatedBy(creator);
        subject.setModifiedBy(creator);
        subject = subjectRepository.save(subject);

        subject.setSubjectCode(String.format("SUBJ-%06d", subject.getId()));
        subject = subjectRepository.save(subject);

        for (EligibilityCriterion criterion : criteria) {
            SubjectEligibilityAnswer answer = new SubjectEligibilityAnswer();
            answer.setSubject(subject);
            answer.setCriterion(criterion);
            answer.setMet(answersByCriterionId.get(criterion.getId()).met());
            answerRepository.save(answer);
        }

        auditService.record(
                "Subject", String.valueOf(subject.getId()), AuditAction.CREATE,
                null, "enrolled subject " + subject.getSubjectCode() + " under study " + study.getStudyCode(), null);

        visitSchedulingService.generateForSubject(subject);

        User taskOwner = site.getAssignedCra() != null ? site.getAssignedCra() : creator;
        User escalationTarget = study.getCreatedBy();
        taskService.createTask(
                "SUBJECT_ENROLLED",
                "Review new subject enrollment: " + subject.getSubjectCode(),
                "Subject " + subject.getSubjectCode() + " was enrolled under study " + study.getStudyCode() + ".",
                "Subject", subject.getId(), taskOwner.getId(), escalationTarget.getId(), creatorUsername);

        return SubjectResponse.from(subject, currentRoleCodes());
    }

    @Transactional
    public SubjectResponse updateSubject(Long id, UpdateSubjectRequest req, String updaterUsername) {
        Subject subject = findSubject(id);
        User updater = currentUser(updaterUsername);

        subject.setFirstName(req.firstName());
        subject.setLastName(req.lastName());
        subject.setGender(req.gender());
        subject.setContactPhone(req.contactPhone());
        subject.setContactEmail(req.contactEmail());
        subject.setAddress(req.address());
        subject.setEmergencyContact(req.emergencyContact());
        subject.setNotes(req.notes());
        subject.setMedicalHistory(req.medicalHistory());
        subject.setModifiedBy(updater);
        subject = subjectRepository.save(subject);

        auditService.record("Subject", String.valueOf(id), AuditAction.UPDATE, null, "subject details updated", null);
        return SubjectResponse.from(subject, currentRoleCodes());
    }

    /** BL Epic 10 Story 05 -- restricted to contact/demographic fields only; the patient's own
     * subject ID is always resolved server-side by the caller (PatientProfileController via
     * PatientContextService), never trusted from a client-supplied value. */
    @Transactional
    public SubjectResponse updateOwnProfile(Long id, UpdateOwnProfileRequest req, String actorUsername) {
        Subject subject = findSubject(id);
        User actor = currentUser(actorUsername);

        subject.setContactPhone(req.contactPhone());
        subject.setContactEmail(req.contactEmail());
        subject.setAddress(req.address());
        subject.setEmergencyContact(req.emergencyContact());
        subject.setModifiedBy(actor);
        subject = subjectRepository.save(subject);

        auditService.record("Subject", String.valueOf(id), AuditAction.UPDATE, null, "patient self-updated contact profile", null);
        return SubjectResponse.from(subject, currentRoleCodes());
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(Long id) {
        return SubjectResponse.from(findSubject(id), currentRoleCodes());
    }

    @Transactional(readOnly = true)
    public Page<SubjectResponse> list(Long studyId, Long siteId, String search, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search;
        Set<String> roles = currentRoleCodes();
        return subjectRepository.search(studyId, siteId, normalizedSearch, pageable)
                .map(s -> SubjectResponse.from(s, roles));
    }

    Subject findSubject(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new SubjectNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private Set<String> currentRoleCodes() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }
}
