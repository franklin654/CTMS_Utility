package com.ctms.ctms_backend.security;

import com.ctms.ctms_backend.security.exception.PasswordPolicyViolationException;
import com.ctms.ctms_backend.user.PasswordHistoryEntry;
import com.ctms.ctms_backend.user.PasswordHistoryRepository;
import com.ctms.ctms_backend.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 12;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordPolicyProperties properties;
    private final PasswordEncoder passwordEncoder;

    public PasswordPolicyValidator(
            PasswordHistoryRepository passwordHistoryRepository,
            PasswordPolicyProperties properties,
            PasswordEncoder passwordEncoder) {
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    /** Throws {@link PasswordPolicyViolationException} if the candidate password fails complexity
     * or history rules for the given user. Call before hashing/saving a new password. */
    public void validate(User user, String rawPassword) {
        List<String> violations = new ArrayList<>();
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            violations.add("must be at least " + MIN_LENGTH + " characters");
        }
        if (rawPassword == null || !UPPERCASE.matcher(rawPassword).find()) {
            violations.add("must contain an uppercase letter");
        }
        if (rawPassword == null || !LOWERCASE.matcher(rawPassword).find()) {
            violations.add("must contain a lowercase letter");
        }
        if (rawPassword == null || !DIGIT.matcher(rawPassword).find()) {
            violations.add("must contain a digit");
        }
        if (rawPassword == null || !SPECIAL.matcher(rawPassword).find()) {
            violations.add("must contain a special character");
        }
        if (rawPassword != null && user.getUsername() != null
                && rawPassword.toLowerCase().contains(user.getUsername().toLowerCase())) {
            violations.add("must not contain the username");
        }
        if (!violations.isEmpty()) {
            throw new PasswordPolicyViolationException(violations);
        }

        if (user.getId() != null && user.getPasswordHash() != null && properties.historySize() > 0) {
            List<PasswordHistoryEntry> recent = passwordHistoryRepository.findByUserOrderByCreatedAtDesc(
                    user, PageRequest.of(0, properties.historySize()));
            boolean reused = recent.stream().anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
            if (reused || passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new PasswordPolicyViolationException(
                        List.of("must not reuse any of the last " + properties.historySize() + " passwords"));
            }
        }
    }
}
