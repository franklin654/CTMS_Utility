package com.ctms.ctms_backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.password")
public record PasswordPolicyProperties(
        int maxFailedAttempts, int lockoutDurationMinutes, int expiryDays, int historySize) {}
