package com.ctms.ctms_backend.user.controller;

import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.user.dto.UserSummaryResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Small user-lookup endpoint supporting role-scoped assignment pickers (e.g. Site's
 * "assign CRA" autocomplete) -- not a general user directory, so it's gated to the same
 * roles that can perform those assignments rather than opened up broadly. */
@RestController
@RequestMapping("/api/users")
public class UserLookupController {

    private final UserRepository userRepository;

    public UserLookupController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDY_MANAGER', 'ADMIN')")
    public List<UserSummaryResponse> search(
            @RequestParam String role, @RequestParam(required = false) String search) {
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search;
        return userRepository.searchByRole(role, normalizedSearch).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }
}
