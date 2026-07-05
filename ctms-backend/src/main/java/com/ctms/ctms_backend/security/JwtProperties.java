package com.ctms.ctms_backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long accessTokenExpiryMinutes, long refreshTokenExpiryDays) {}
