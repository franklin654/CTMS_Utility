package com.ctms.ctms_backend.subject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.PasswordPolicyProperties;
import com.ctms.ctms_backend.security.PasswordPolicyValidator;
import com.ctms.ctms_backend.subject.dto.PortalAccountResponse;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.NoPortalAccountException;
import com.ctms.ctms_backend.subject.exception.PortalAccountAlreadyExistsException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.PasswordHistoryRepository;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SubjectPortalAccountServiceTest {

    @Mock private SubjectRepository subjectRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditService auditService;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;

    // Real validator + real encoder -- the test needs to prove the deterministic password formula
    // actually satisfies the real policy, not a mocked one.
    private final PasswordPolicyValidator passwordPolicyValidator =
            new PasswordPolicyValidator(passwordHistoryRepositoryStub(), new PasswordPolicyProperties(5, 30, 90, 5), new BCryptPasswordEncoder());

    private SubjectPortalAccountService service;

    private Subject subject;

    @BeforeEach
    void setUp() {
        service = new SubjectPortalAccountService(
                subjectRepository, userRepository, roleRepository, new BCryptPasswordEncoder(), passwordPolicyValidator, auditService);

        subject = new Subject();
        subject.setId(1L);
        subject.setSubjectCode("SUBJ-000010");
        subject.setFirstName("Jane");
        subject.setLastName("Doe");
        subject.setDateOfBirth(LocalDate.of(1990, 1, 1));

        lenient().when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(100L);
            }
            return u;
        });
        Role patientRole = new Role();
        patientRole.setCode(Role.PATIENT_SUBJECT);
        lenient().when(roleRepository.findByCode(Role.PATIENT_SUBJECT)).thenReturn(Optional.of(patientRole));
    }

    private static PasswordHistoryRepository passwordHistoryRepositoryStub() {
        PasswordHistoryRepository repo = org.mockito.Mockito.mock(PasswordHistoryRepository.class);
        lenient().when(repo.findByUserOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        return repo;
    }

    @Test
    void createPortalAccount_succeeds_usernameAndPasswordDerivedDeterministically() {
        PortalAccountResponse response = service.createPortalAccount(1L, "coordinator1");

        assertEquals("subj-000010", response.username());
        assertEquals("Jane@01011990#1", response.temporaryPassword());
        assertNotNull(subject.getLinkedUser());
        assertTrue(new BCryptPasswordEncoder().matches("Jane@01011990#1", subject.getLinkedUser().getPasswordHash()));
    }

    @Test
    void createPortalAccount_alreadyLinked_throws() {
        User existing = new User();
        existing.setId(50L);
        subject.setLinkedUser(existing);

        assertThrows(PortalAccountAlreadyExistsException.class, () -> service.createPortalAccount(1L, "coordinator1"));
    }

    @Test
    void resetPortalPassword_noAccount_throws() {
        assertThrows(NoPortalAccountException.class, () -> service.resetPortalPassword(1L, "coordinator1"));
    }

    @Test
    void resetPortalPassword_succeeds_recomputesFromCurrentSubjectData() {
        User existing = new User();
        existing.setId(50L);
        existing.setUsername("subj-000010");
        existing.setPasswordHash(new BCryptPasswordEncoder().encode("OldPass@12345#9"));
        subject.setLinkedUser(existing);

        PortalAccountResponse response = service.resetPortalPassword(1L, "coordinator1");

        assertEquals("subj-000010", response.username());
        assertEquals("Jane@01011990#1", response.temporaryPassword());
        assertTrue(new BCryptPasswordEncoder().matches("Jane@01011990#1", existing.getPasswordHash()));
    }
}
