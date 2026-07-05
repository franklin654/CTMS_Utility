package com.ctms.ctms_backend.user.dto;

import com.ctms.ctms_backend.user.User;

public record UserSummaryResponse(Long id, String username, String fullName) {

    public static UserSummaryResponse from(User u) {
        return new UserSummaryResponse(u.getId(), u.getUsername(), u.getFullName());
    }
}
