package com.ctms.ctms_backend.security;

import com.ctms.ctms_backend.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Issues and parses short-lived JWT access tokens. Refresh tokens are opaque (see {@link com.ctms.ctms_backend.security.token.SecureTokens}), not JWTs, so they stay server-revocable. */
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "uid";

    private final Key signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(properties.secret()));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).collect(Collectors.toList());
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(USER_ID_CLAIM, user.getId())
                .claim(ROLES_CLAIM, roles)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(properties.accessTokenExpiryMinutes(), ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public long accessTokenExpirySeconds() {
        return properties.accessTokenExpiryMinutes() * 60;
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(properties.refreshTokenExpiryDays(), ChronoUnit.DAYS);
    }

    /** Returns empty if the token is missing, malformed, expired, or has a bad signature. */
    public Optional<ParsedToken> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build()
                    .parseSignedClaims(token)
                    .getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(ROLES_CLAIM, List.class);
            Long userId = claims.get(USER_ID_CLAIM, Long.class);
            return Optional.of(new ParsedToken(claims.getSubject(), userId, roles));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record ParsedToken(String username, Long userId, List<String> roles) {}
}
