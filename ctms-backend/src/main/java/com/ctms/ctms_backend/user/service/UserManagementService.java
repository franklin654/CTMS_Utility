package com.ctms.ctms_backend.user.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.PasswordPolicyValidator;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.user.dto.CreateUserRequest;
import com.ctms.ctms_backend.user.dto.CreateUserResponse;
import com.ctms.ctms_backend.user.dto.UserResponse;
import com.ctms.ctms_backend.user.exception.DuplicateUserException;
import com.ctms.ctms_backend.user.exception.InvalidRoleException;
import com.ctms.ctms_backend.user.exception.LastAdminException;
import com.ctms.ctms_backend.user.exception.UserNotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only user provisioning and role management -- the "user management" capability the RBAC
 * matrix has always listed as an Admin responsibility, but which (until now) only existed as a
 * one-time env-var bootstrap ({@code AdminBootstrapRunner}) or direct SQL against the users table.
 */
@Service
public class UserManagementService {

    private static final String TEMP_PASSWORD_CHARS_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TEMP_PASSWORD_CHARS_LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_PASSWORD_CHARS_DIGIT = "23456789";
    private static final String TEMP_PASSWORD_CHARS_SPECIAL = "!@#$%&*";
    private static final int TEMP_PASSWORD_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;

    public UserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("username", request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("email", request.email());
        }
        Set<Role> roles = resolveRoles(request.roles());

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRoles(roles);

        String temporaryPassword = generateTemporaryPassword();
        passwordPolicyValidator.validate(user, temporaryPassword);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        // Force a password change on first login -- same mechanism AuthenticationService already
        // uses for expired passwords, just triggered immediately rather than after N days.
        user.setPasswordExpiresAt(Instant.now().minusSeconds(1));

        user = userRepository.save(user);
        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.CREATE, null,
                "created with roles " + request.roles(), null);

        return new CreateUserResponse(UserResponse.from(user), temporaryPassword);
    }

    @Transactional
    public UserResponse updateRoles(Long userId, Set<String> roleCodes) {
        User user = findUser(userId);
        Set<Role> newRoles = resolveRoles(roleCodes);

        boolean wasAdmin = user.hasRole(Role.ADMIN);
        boolean staysAdmin = newRoles.stream().anyMatch(r -> r.getCode().equals(Role.ADMIN));
        if (wasAdmin && !staysAdmin && isLastEnabledAdmin(user)) {
            throw new LastAdminException();
        }

        Set<String> previousRoles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        user.setRoles(newRoles);
        user = userRepository.save(user);

        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.UPDATE,
                "roles: " + previousRoles, "roles: " + roleCodes, null);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(Long userId, boolean enabled) {
        User user = findUser(userId);
        if (!enabled && user.hasRole(Role.ADMIN) && isLastEnabledAdmin(user)) {
            throw new LastAdminException();
        }

        boolean previous = user.isEnabled();
        user.setEnabled(enabled);
        if (enabled) {
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
        }
        user = userRepository.save(user);

        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.UPDATE,
                "enabled: " + previous, "enabled: " + enabled, null);

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<String> listRoleCodes() {
        return roleRepository.findAll().stream().map(Role::getCode).sorted().toList();
    }

    private boolean isLastEnabledAdmin(User user) {
        return userRepository.findByRoles_Code(Role.ADMIN).stream()
                .filter(User::isEnabled)
                .filter(u -> !u.getId().equals(user.getId()))
                .findAny()
                .isEmpty();
    }

    private Set<Role> resolveRoles(Set<String> roleCodes) {
        Set<Role> roles = new HashSet<>();
        for (String code : roleCodes) {
            roles.add(roleRepository.findByCode(code).orElseThrow(() -> new InvalidRoleException(code)));
        }
        return roles;
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /** Guarantees at least one of each required character class, satisfying
     * {@link PasswordPolicyValidator} regardless of shuffle outcome. */
    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        sb.append(randomChar(TEMP_PASSWORD_CHARS_UPPER));
        sb.append(randomChar(TEMP_PASSWORD_CHARS_LOWER));
        sb.append(randomChar(TEMP_PASSWORD_CHARS_DIGIT));
        sb.append(randomChar(TEMP_PASSWORD_CHARS_SPECIAL));
        String all = TEMP_PASSWORD_CHARS_UPPER + TEMP_PASSWORD_CHARS_LOWER + TEMP_PASSWORD_CHARS_DIGIT
                + TEMP_PASSWORD_CHARS_SPECIAL;
        for (int i = sb.length(); i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(randomChar(all));
        }
        // Shuffle so the guaranteed character classes aren't always in the same 4 positions.
        List<Character> chars = new java.util.ArrayList<>();
        for (int i = 0; i < sb.length(); i++) {
            chars.add(sb.charAt(i));
        }
        java.util.Collections.shuffle(chars, random);
        StringBuilder shuffled = new StringBuilder(chars.size());
        chars.forEach(shuffled::append);
        return shuffled.toString();
    }

    private char randomChar(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
