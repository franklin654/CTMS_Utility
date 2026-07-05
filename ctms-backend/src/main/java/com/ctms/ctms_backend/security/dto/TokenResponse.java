package com.ctms.ctms_backend.security.dto;

public record TokenResponse(
        String accessToken, String refreshToken, long expiresInSeconds, boolean mustChangePassword) {}
