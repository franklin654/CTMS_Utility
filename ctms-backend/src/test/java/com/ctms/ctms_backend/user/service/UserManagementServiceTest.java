package com.ctms.ctms_backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.PasswordPolicyValidator;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.user.dto.CreateUserRequest;
import com.ctms.ctms_backend.user.exception.DuplicateUserException;
import com.ctms.ctms_backend.user.exception.InvalidRoleException;
import com.ctms.ctms_backend.user.exception.LastAdminException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyValidator passwordPolicyValidator;
    @Mock private AuditService auditService;

    private UserManagementService service;

    private Role studyManagerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(userRepository, roleRepository, passwordEncoder, passwordPolicyValidator, auditService);

        studyManagerRole = new Role();
        studyManagerRole.setId(1L);
        studyManagerRole.setCode(Role.STUDY_MANAGER);

        adminRole = new Role();
        adminRole.setId(2L);
        adminRole.setCode(Role.ADMIN);

        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(99L);
            }
            return u;
        });
    }

    @Test
    void createUser_success_assignsRolesAndAudits() {
        when(userRepository.existsByUsername("jsmith")).thenReturn(false);
        when(userRepository.existsByEmail("jsmith@ctms.local")).thenReturn(false);
        when(roleRepository.findByCode(Role.STUDY_MANAGER)).thenReturn(Optional.of(studyManagerRole));

        var response = service.createUser(
                new CreateUserRequest("jsmith", "jsmith@ctms.local", "Jane Smith", Set.of(Role.STUDY_MANAGER)));

        assertEquals("jsmith", response.user().username());
        assertEquals(Set.of(Role.STUDY_MANAGER), response.user().roles());
        assertEquals(16, response.temporaryPassword().length());
        verify(passwordPolicyValidator).validate(any(User.class), anyString());
        verify(auditService).record(eq("User"), eq("99"), eq(AuditAction.CREATE), any(), anyString(), any());
    }

    @Test
    void createUser_duplicateUsername_throws() {
        when(userRepository.existsByUsername("jsmith")).thenReturn(true);

        assertThrows(
                DuplicateUserException.class,
                () -> service.createUser(
                        new CreateUserRequest("jsmith", "jsmith@ctms.local", "Jane Smith", Set.of(Role.STUDY_MANAGER))));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_unknownRoleCode_throws() {
        when(userRepository.existsByUsername("jsmith")).thenReturn(false);
        when(userRepository.existsByEmail("jsmith@ctms.local")).thenReturn(false);
        when(roleRepository.findByCode("NOT_A_ROLE")).thenReturn(Optional.empty());

        assertThrows(
                InvalidRoleException.class,
                () -> service.createUser(
                        new CreateUserRequest("jsmith", "jsmith@ctms.local", "Jane Smith", Set.of("NOT_A_ROLE"))));
    }

    @Test
    void updateRoles_removingLastAdminRole_throws() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin1");
        admin.setEnabled(true);
        admin.setRoles(Set.of(adminRole));

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCode(Role.STUDY_MANAGER)).thenReturn(Optional.of(studyManagerRole));
        when(userRepository.findByRoles_Code(Role.ADMIN)).thenReturn(List.of(admin));

        assertThrows(LastAdminException.class, () -> service.updateRoles(1L, Set.of(Role.STUDY_MANAGER)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_notLastAdmin_succeeds() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin1");
        admin.setEnabled(true);
        admin.setRoles(Set.of(adminRole));

        User otherAdmin = new User();
        otherAdmin.setId(2L);
        otherAdmin.setEnabled(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCode(Role.STUDY_MANAGER)).thenReturn(Optional.of(studyManagerRole));
        when(userRepository.findByRoles_Code(Role.ADMIN)).thenReturn(List.of(admin, otherAdmin));

        var response = service.updateRoles(1L, Set.of(Role.STUDY_MANAGER));

        assertEquals(Set.of(Role.STUDY_MANAGER), response.roles());
        verify(auditService).record(eq("User"), eq("1"), eq(AuditAction.UPDATE), anyString(), anyString(), any());
    }

    @Test
    void setEnabled_disablingLastAdmin_throws() {
        User admin = new User();
        admin.setId(1L);
        admin.setEnabled(true);
        admin.setRoles(Set.of(adminRole));

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRoles_Code(Role.ADMIN)).thenReturn(List.of(admin));

        assertThrows(LastAdminException.class, () -> service.setEnabled(1L, false));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_reEnabling_resetsLockout() {
        User user = new User();
        user.setId(3L);
        user.setEnabled(false);
        user.setAccountLocked(true);
        user.setFailedLoginAttempts(5);
        user.setRoles(Set.of(studyManagerRole));

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        var response = service.setEnabled(3L, true);

        assertEquals(true, response.enabled());
        assertEquals(false, user.isAccountLocked());
        assertEquals(0, user.getFailedLoginAttempts());
    }
}
