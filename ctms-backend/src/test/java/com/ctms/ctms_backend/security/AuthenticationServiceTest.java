package com.ctms.ctms_backend.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.MailService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.security.exception.InvalidTokenException;
import com.ctms.ctms_backend.user.PasswordHistoryRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.user.exception.DuplicateUserException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/** BL Epic 11 Phase 13 compliance audit finding -- AuthenticationService previously had zero
 * audit calls outside login(); this test proves refresh/logout/changePassword/
 * requestPasswordReset/resetPassword all now write an AuditLog entry for the affected User. */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyValidator passwordPolicyValidator;
    @Mock private JwtService jwtService;
    @Mock private MailService mailService;
    @Mock private AuditService auditService;
    @Mock private PlatformTransactionManager transactionManager;

    private AuthenticationService authenticationService;
    private User user;

    @BeforeEach
    void setUp() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties(5, 15, 90, 5);
        authenticationService = new AuthenticationService(
                userRepository, passwordHistoryRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, passwordPolicyValidator, properties, jwtService, mailService, auditService,
                transactionManager);

        user = new User();
        user.setId(1L);
        user.setUsername("jdoe");
        user.setEmail("jdoe@ctms.local");
        user.setPasswordHash("old-hash");

        lenient().when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        lenient().when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));
        lenient().when(jwtService.accessTokenExpirySeconds()).thenReturn(900L);
    }

    @Test
    void refresh_recordsAuditEntryForUser() {
        RefreshToken token = new RefreshToken(user, "hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        authenticationService.refresh("raw-token");

        verify(auditService).record(eq("User"), eq("1"), eq(AuditAction.STATE_CHANGE), any(), anyString(), any());
    }

    @Test
    void refresh_invalidToken_throwsAndDoesNotAudit() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authenticationService.refresh("bad-token"));

        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void logout_revokesTokenAndRecordsAuditEntry() {
        RefreshToken token = new RefreshToken(user, "hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        authenticationService.logout("raw-token");

        verify(auditService).record(eq("User"), eq("1"), eq(AuditAction.STATE_CHANGE), any(), anyString(), any());
    }

    @Test
    void logout_unknownToken_doesNotAudit() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authenticationService.logout("unknown-token");

        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void changePassword_recordsAuditEntryNotingSessionInvalidation() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);

        authenticationService.changePassword("jdoe", "old-pass", "New!Pass2026");

        verify(refreshTokenRepository).deleteByUser(user);
        verify(auditService).record(
                eq("User"), eq("1"), eq(AuditAction.UPDATE), any(),
                org.mockito.ArgumentMatchers.contains("sessions invalidated"), any());
    }

    @Test
    void changeUsername_correctPassword_updatesUsernameInvalidatesSessionsAndAudits() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);

        authenticationService.changeUsername("jdoe", "old-pass", "newname");

        verify(userRepository).save(user);
        verify(refreshTokenRepository).deleteByUser(user);
        verify(auditService).record(
                eq("User"), eq("1"), eq(AuditAction.UPDATE), anyString(),
                org.mockito.ArgumentMatchers.contains("all active sessions invalidated"), any());
    }

    @Test
    void changeUsername_wrongPassword_throwsInvalidCredentialsAndDoesNotAudit() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "old-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.changeUsername("jdoe", "wrong-pass", "newname"));

        verify(refreshTokenRepository, never()).deleteByUser(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void changeUsername_duplicateUsername_throwsDuplicateUserExceptionAndDoesNotMutate() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThrows(DuplicateUserException.class,
                () -> authenticationService.changeUsername("jdoe", "old-pass", "taken"));

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    void changeUsername_sameUsernameAsCurrent_isNoOpAndDoesNotAuditOrInvalidateSessions() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);

        authenticationService.changeUsername("jdoe", "old-pass", "jdoe");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByUser(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void changeEmail_correctPassword_updatesEmailAndAuditsWithoutInvalidatingSessions() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);

        authenticationService.changeEmail("jdoe", "old-pass", "new@ctms.local");

        verify(userRepository).save(user);
        verify(refreshTokenRepository, never()).deleteByUser(any());
        verify(auditService).record(eq("User"), eq("1"), eq(AuditAction.UPDATE), anyString(), anyString(), any());
    }

    @Test
    void changeEmail_wrongPassword_throwsInvalidCredentialsAndDoesNotAudit() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "old-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.changeEmail("jdoe", "wrong-pass", "new@ctms.local"));

        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void changeEmail_duplicateEmail_throwsDuplicateUserExceptionAndDoesNotMutate() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(userRepository.existsByEmail("taken@ctms.local")).thenReturn(true);

        assertThrows(DuplicateUserException.class,
                () -> authenticationService.changeEmail("jdoe", "old-pass", "taken@ctms.local"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeEmail_sameEmailAsCurrent_isNoOp() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);

        authenticationService.changeEmail("jdoe", "old-pass", "jdoe@ctms.local");

        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void requestPasswordReset_knownEmail_recordsAuditEntry() {
        when(userRepository.findByEmail("jdoe@ctms.local")).thenReturn(Optional.of(user));

        authenticationService.requestPasswordReset("jdoe@ctms.local");

        verify(auditService).record(eq("User"), eq("1"), eq(AuditAction.STATE_CHANGE), any(), anyString(), any());
    }

    @Test
    void requestPasswordReset_unknownEmail_doesNotAudit() {
        when(userRepository.findByEmail("nobody@ctms.local")).thenReturn(Optional.empty());

        authenticationService.requestPasswordReset("nobody@ctms.local");

        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), any());
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_recordsAuditEntryNotingSessionInvalidation() {
        PasswordResetToken resetToken = new PasswordResetToken(user, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));

        authenticationService.resetPassword("raw-token", "New!Pass2026");

        verify(refreshTokenRepository).deleteByUser(user);
        verify(auditService, times(1)).record(
                eq("User"), eq("1"), eq(AuditAction.UPDATE), any(),
                org.mockito.ArgumentMatchers.contains("sessions invalidated"), any());
    }
}
