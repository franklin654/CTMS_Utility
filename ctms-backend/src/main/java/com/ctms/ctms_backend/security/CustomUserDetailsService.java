package com.ctms.ctms_backend.security;

import com.ctms.ctms_backend.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Present mainly so Spring Security doesn't fall back to its auto-generated in-memory user.
 * Per-request authentication is actually driven by {@link JwtAuthenticationFilter} reading JWT
 * claims directly; this service is for any future admin/password-grant flows that need it.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository
                .findByUsername(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with username " + username));
    }
}
