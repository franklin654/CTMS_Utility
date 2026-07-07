package com.ctms.ctms_backend.security;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.MailService;
import com.ctms.ctms_backend.security.dto.TokenResponse;
import com.ctms.ctms_backend.security.exception.AccountLockedException;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.security.exception.InvalidTokenException;
import com.ctms.ctms_backend.security.token.SecureTokens;
import com.ctms.ctms_backend.user.PasswordHistoryEntry;
import com.ctms.ctms_backend.user.PasswordHistoryRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.user.exception.DuplicateUserException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordPolicyProperties passwordPolicyProperties;
    private final JwtService jwtService;
    private final MailService mailService;
    private final AuditService auditService;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordHistoryRepository passwordHistoryRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            PasswordPolicyProperties passwordPolicyProperties,
            JwtService jwtService,
            MailService mailService,
            AuditService auditService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordPolicyProperties = passwordPolicyProperties;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.auditService = auditService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public TokenResponse login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);

        if (user.isAccountLocked()) {
            Instant lockedUntil = user.getLockedAt().plus(passwordPolicyProperties.lockoutDurationMinutes(), ChronoUnit.MINUTES);
            if (lockedUntil.isAfter(Instant.now())) {
                throw new AccountLockedException(lockedUntil);
            }
            // Lockout window has elapsed: allow this attempt to proceed and re-evaluate below.
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
        }

        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            if (user.isEnabled()) {
                registerFailedAttempt(user);
                auditService.record(
                        "User", String.valueOf(user.getId()), AuditAction.LOGIN_FAILED, null, null, null);
            }
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedAt(null);
        userRepository.save(user);
        auditService.record("User", String.valueOf(user.getId()), AuditAction.LOGIN, null, null, null);

        boolean mustChangePassword =
                user.getPasswordExpiresAt() != null && user.getPasswordExpiresAt().isBefore(Instant.now());

        return issueTokens(user, mustChangePassword);
    }

    /**
     * Runs in its own REQUIRES_NEW transaction so the failed-attempt count survives even though the
     * caller's transaction (login()) is about to roll back when it throws InvalidCredentialsException.
     */
    private void registerFailedAttempt(User user) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            User fresh = userRepository.findById(user.getId()).orElseThrow();
            fresh.setFailedLoginAttempts(fresh.getFailedLoginAttempts() + 1);
            if (fresh.getFailedLoginAttempts() >= passwordPolicyProperties.maxFailedAttempts()) {
                fresh.setAccountLocked(true);
                fresh.setLockedAt(Instant.now());
            }
            userRepository.save(fresh);
        });
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = SecureTokens.sha256(rawRefreshToken);
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        if (!token.isValid()) {
            throw new InvalidTokenException("Refresh token expired or revoked");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        User user = token.getUser();
        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.STATE_CHANGE, null, "access token refreshed", null);

        boolean mustChangePassword =
                user.getPasswordExpiresAt() != null && user.getPasswordExpiresAt().isBefore(Instant.now());
        return issueTokens(user, mustChangePassword);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = SecureTokens.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            auditService.record(
                    "User", String.valueOf(token.getUser().getId()), AuditAction.STATE_CHANGE, null, "session revoked (logout)", null);
        });
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        applyNewPassword(user, newPassword);
        refreshTokenRepository.deleteByUser(user);
        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.UPDATE, null,
                "password changed; all active sessions invalidated", null);
    }

    @Transactional
    public void changeUsername(String currentUsername, String currentPassword, String newUsername) {
        User user = userRepository.findByUsername(currentUsername).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (newUsername.equals(currentUsername)) {
            return;
        }
        if (userRepository.existsByUsername(newUsername)) {
            throw new DuplicateUserException("username", newUsername);
        }
        String oldUsername = user.getUsername();
        user.setUsername(newUsername);
        userRepository.save(user);
        // Username is the JWT subject and the key PatientContextService/Principal.getName() lookups
        // use, so any still-live access token would carry a now-stale claim -- force full re-login.
        refreshTokenRepository.deleteByUser(user);
        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.UPDATE,
                "username: " + oldUsername,
                "username: " + newUsername + "; all active sessions invalidated", null);
    }

    @Transactional
    public void changeEmail(String username, String currentPassword, String newEmail) {
        User user = userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (newEmail.equals(user.getEmail())) {
            return;
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new DuplicateUserException("email", newEmail);
        }
        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        userRepository.save(user);
        // Deliberately no refreshTokenRepository.deleteByUser() here: email is not the JWT subject
        // or a security-context lookup key, so no live session becomes stale. Do not "fix" this into
        // symmetry with changeUsername.
        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.UPDATE,
                "email: " + oldEmail, "email: " + newEmail, null);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = SecureTokens.generate();
            String hash = SecureTokens.sha256(rawToken);
            PasswordResetToken resetToken =
                    new PasswordResetToken(user, hash, Instant.now().plus(30, ChronoUnit.MINUTES));
            passwordResetTokenRepository.save(resetToken);
            mailService.send(
                    user.getEmail(),
                    "CTMS password reset",
                    "A password reset was requested for your account. Reset token (valid 30 minutes): "
                            + rawToken);
            auditService.record(
                    "User", String.valueOf(user.getId()), AuditAction.STATE_CHANGE, null, "password reset requested", null);
        });
        // Deliberately silent if the email is unknown, to avoid user enumeration -- including no
        // audit entry, since there is no real User record to attach one to.
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hash = SecureTokens.sha256(rawToken);
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));
        if (!resetToken.isValid()) {
            throw new InvalidTokenException("Invalid or expired reset token");
        }
        User user = resetToken.getUser();
        applyNewPassword(user, newPassword);
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.deleteByUser(user);
        auditService.record(
                "User", String.valueOf(user.getId()), AuditAction.UPDATE, null,
                "password reset via token; all active sessions invalidated", null);
    }

    private void applyNewPassword(User user, String newPassword) {
        passwordPolicyValidator.validate(user, newPassword);
        if (user.getPasswordHash() != null) {
            passwordHistoryRepository.save(new PasswordHistoryEntry(user, user.getPasswordHash()));
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setPasswordExpiresAt(Instant.now().plus(passwordPolicyProperties.expiryDays(), ChronoUnit.DAYS));
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedAt(null);
        userRepository.save(user);
    }

    private TokenResponse issueTokens(User user, boolean mustChangePassword) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = SecureTokens.generate();
        RefreshToken refreshToken =
                new RefreshToken(user, SecureTokens.sha256(rawRefreshToken), jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);
        return new TokenResponse(
                accessToken, rawRefreshToken, jwtService.accessTokenExpirySeconds(), mustChangePassword);
    }
}
