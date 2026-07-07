package com.ctms.ctms_backend.user.dto;

import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean enabled,
        boolean accountLocked,
        Set<String> roles,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.isEnabled(),
                u.isAccountLocked(),
                u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                u.getCreatedAt());
    }
}
