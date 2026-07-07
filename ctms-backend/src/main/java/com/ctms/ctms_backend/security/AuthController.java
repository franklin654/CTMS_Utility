package com.ctms.ctms_backend.security;

import com.ctms.ctms_backend.security.dto.ChangeEmailRequest;
import com.ctms.ctms_backend.security.dto.ChangePasswordRequest;
import com.ctms.ctms_backend.security.dto.ChangeUsernameRequest;
import com.ctms.ctms_backend.security.dto.ForgotPasswordRequest;
import com.ctms.ctms_backend.security.dto.LoginRequest;
import com.ctms.ctms_backend.security.dto.RefreshRequest;
import com.ctms.ctms_backend.security.dto.ResetPasswordRequest;
import com.ctms.ctms_backend.security.dto.TokenResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authenticationService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(principal.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/username")
    public ResponseEntity<Void> changeUsername(Principal principal, @Valid @RequestBody ChangeUsernameRequest request) {
        authenticationService.changeUsername(principal.getName(), request.currentPassword(), request.newUsername());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/email")
    public ResponseEntity<Void> changeEmail(Principal principal, @Valid @RequestBody ChangeEmailRequest request) {
        authenticationService.changeEmail(principal.getName(), request.currentPassword(), request.newEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
