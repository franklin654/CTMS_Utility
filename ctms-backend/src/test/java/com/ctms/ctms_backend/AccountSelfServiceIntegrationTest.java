package com.ctms.ctms_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ctms.ctms_backend.security.AuthenticationService;
import com.ctms.ctms_backend.security.dto.TokenResponse;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.security.exception.InvalidTokenException;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.user.exception.DuplicateUserException;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars), not Testcontainers,
 * since Docker isn't available in this environment. Rolled back after each test.
 * Covers the self-service username/email change endpoints added to AuthenticationService. */
@SpringBootTest
@Transactional
class AccountSelfServiceIntegrationTest {

    @Autowired private AuthenticationService authenticationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName("Account Self-Service Test User");
        user.setPasswordHash(passwordEncoder.encode("Old!Pass2026"));
        Role role = roleRepository.findByCode(Role.SITE_COORDINATOR).orElseThrow();
        user.setRoles(new HashSet<>(List.of(role)));
        return userRepository.save(user);
    }

    @Test
    void changeUsername_thenOldRefreshTokenIsRejected_newLoginWithNewUsernameSucceeds() {
        createUser("acct-user1", "acct-user1@ctms.local");
        TokenResponse tokens = authenticationService.login("acct-user1", "Old!Pass2026");

        authenticationService.changeUsername("acct-user1", "Old!Pass2026", "acct-user1-renamed");

        assertThrows(InvalidTokenException.class, () -> authenticationService.refresh(tokens.refreshToken()));

        TokenResponse newTokens = authenticationService.login("acct-user1-renamed", "Old!Pass2026");
        assertNotEquals(tokens.accessToken(), newTokens.accessToken());
    }

    @Test
    void changeUsername_collisionWithExistingUser_throwsDuplicateUserException() {
        createUser("acct-user2", "acct-user2@ctms.local");
        createUser("acct-user3", "acct-user3@ctms.local");

        assertThrows(DuplicateUserException.class,
                () -> authenticationService.changeUsername("acct-user2", "Old!Pass2026", "acct-user3"));
    }

    @Test
    void changeEmail_collisionWithExistingUser_throwsDuplicateUserException() {
        createUser("acct-user4", "acct-user4@ctms.local");
        createUser("acct-user5", "acct-user5@ctms.local");

        assertThrows(DuplicateUserException.class,
                () -> authenticationService.changeEmail("acct-user4", "Old!Pass2026", "acct-user5@ctms.local"));
    }

    @Test
    void changeEmail_doesNotInvalidateExistingRefreshToken() {
        createUser("acct-user6", "acct-user6@ctms.local");
        TokenResponse tokens = authenticationService.login("acct-user6", "Old!Pass2026");

        authenticationService.changeEmail("acct-user6", "Old!Pass2026", "acct-user6-new@ctms.local");

        TokenResponse refreshed = authenticationService.refresh(tokens.refreshToken());
        assertNotEquals(null, refreshed.accessToken());

        User reloaded = userRepository.findByUsername("acct-user6").orElseThrow();
        assertEquals("acct-user6-new@ctms.local", reloaded.getEmail());
    }

    @Test
    void changeUsername_wrongCurrentPassword_throwsInvalidCredentials() {
        createUser("acct-user7", "acct-user7@ctms.local");

        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.changeUsername("acct-user7", "wrong-password", "acct-user7-renamed"));
    }

    @Test
    void changeEmail_wrongCurrentPassword_throwsInvalidCredentials() {
        createUser("acct-user8", "acct-user8@ctms.local");

        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.changeEmail("acct-user8", "wrong-password", "acct-user8-new@ctms.local"));
    }
}
