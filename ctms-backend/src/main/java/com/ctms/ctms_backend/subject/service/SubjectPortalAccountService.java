package com.ctms.ctms_backend.subject.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.PasswordPolicyValidator;
import com.ctms.ctms_backend.subject.dto.PortalAccountResponse;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.NoPortalAccountException;
import com.ctms.ctms_backend.subject.exception.PortalAccountAlreadyExistsException;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 10 Story 01 (Secure Patient Login). This deployment runs on a corporate network where
 * outbound email/SMS may be blocked, so patient accounts are NOT provisioned via the existing
 * email-link password-reset flow -- instead a staff member triggers account creation/reset here,
 * a deterministic temporary password is computed from the subject's own name + DOB, and the
 * subject is forced to change it on first login (via the already-existing passwordExpiresAt /
 * mustChangePassword mechanism -- no new frontend plumbing needed for that part). */
@Service
public class SubjectPortalAccountService {

    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;

    public SubjectPortalAccountService(
            SubjectRepository subjectRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            AuditService auditService) {
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
    }

    @Transactional
    public PortalAccountResponse createPortalAccount(Long subjectId, String actorUsername) {
        Subject subject = findSubject(subjectId);
        if (subject.getLinkedUser() != null) {
            throw new PortalAccountAlreadyExistsException(subjectId);
        }

        String username = deriveUsername(subject);
        String temporaryPassword = deriveTemporaryPassword(subject);

        User user = new User();
        user.setUsername(username);
        user.setEmail(subject.getContactEmail() != null ? subject.getContactEmail() : username + "@patient.local");
        user.setFullName(subject.getFirstName() + " " + subject.getLastName());

        passwordPolicyValidator.validate(user, temporaryPassword);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setPasswordExpiresAt(Instant.now().minusSeconds(1));

        Role patientRole = roleRepository
                .findByCode(Role.PATIENT_SUBJECT)
                .orElseThrow(() -> new IllegalStateException("PATIENT_SUBJECT role missing -- check V1 migration seed data"));
        user.setRoles(new HashSet<>(java.util.List.of(patientRole)));
        user = userRepository.save(user);

        subject.setLinkedUser(user);
        subjectRepository.save(subject);

        auditService.record(
                "Subject", String.valueOf(subjectId), AuditAction.CREATE, null,
                "portal account created (username " + username + ")", null);

        return new PortalAccountResponse(username, temporaryPassword);
    }

    @Transactional
    public PortalAccountResponse resetPortalPassword(Long subjectId, String actorUsername) {
        Subject subject = findSubject(subjectId);
        User user = subject.getLinkedUser();
        if (user == null) {
            throw new NoPortalAccountException(subjectId);
        }

        String temporaryPassword = deriveTemporaryPassword(subject);
        // checkHistory=false -- this recomputes the same deterministic value used at account
        // creation (or a prior reset), by design; the history-reuse rule exists to stop a person
        // from voluntarily cycling back to a password they personally chose, not this.
        passwordPolicyValidator.validate(user, temporaryPassword, false);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setPasswordExpiresAt(Instant.now().minusSeconds(1));
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        auditService.record(
                "Subject", String.valueOf(subjectId), AuditAction.UPDATE, null,
                "portal password reset (username " + user.getUsername() + ")", null);

        return new PortalAccountResponse(user.getUsername(), temporaryPassword);
    }

    private Subject findSubject(Long subjectId) {
        return subjectRepository.findById(subjectId).orElseThrow(() -> new SubjectNotFoundException(subjectId));
    }

    private String deriveUsername(Subject subject) {
        return subject.getSubjectCode().toLowerCase();
    }

    /** Recomputed fresh every time (not frozen at creation) from the subject's CURRENT name/DOB,
     * so a later name correction is reflected the next time an account is created or reset.
     * Engineered to satisfy PasswordPolicyValidator regardless of name length: capitalized first
     * letter (uppercase), remaining letters lowercase, DOB digits, and a fixed "@"/"#1" suffix
     * guaranteeing a special character and minimum length. */
    private String deriveTemporaryPassword(Subject subject) {
        String firstName = subject.getFirstName();
        String capitalized = Character.toUpperCase(firstName.charAt(0))
                + firstName.substring(1).toLowerCase();
        String dob = subject.getDateOfBirth().format(DOB_FORMAT);
        return capitalized + "@" + dob + "#1";
    }
}
