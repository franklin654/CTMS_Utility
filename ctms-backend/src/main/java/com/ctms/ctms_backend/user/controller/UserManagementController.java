package com.ctms.ctms_backend.user.controller;

import com.ctms.ctms_backend.user.dto.CreateUserRequest;
import com.ctms.ctms_backend.user.dto.CreateUserResponse;
import com.ctms.ctms_backend.user.dto.UpdateUserRolesRequest;
import com.ctms.ctms_backend.user.dto.UpdateUserStatusRequest;
import com.ctms.ctms_backend.user.dto.UserResponse;
import com.ctms.ctms_backend.user.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only user provisioning and role management. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public Page<UserResponse> list(@PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        return userManagementService.list(pageable);
    }

    @GetMapping("/roles")
    public List<String> listRoleCodes() {
        return userManagementService.listRoleCodes();
    }

    @PostMapping
    public CreateUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateUserRolesRequest request) {
        return userManagementService.updateRoles(id, request.roles());
    }

    @PutMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userManagementService.setEnabled(id, request.enabled());
    }
}
