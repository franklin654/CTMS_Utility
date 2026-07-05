package com.ctms.ctms_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Builds the Spring Security context directly from a validated access-token's claims -- no DB hit
 * per request, keeping auth stateless. A revoked/disabled user is only shut out once their
 * short-lived access token expires (accepted tradeoff for the ≤2-3s response NFR).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            jwtService.parseAccessToken(token).ifPresent(parsed -> {
                List<GrantedAuthority> authorities = parsed.roles() == null
                        ? List.of()
                        : parsed.roles().stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList();
                var authentication = new UsernamePasswordAuthenticationToken(parsed.username(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
