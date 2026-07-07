package com.ctms.ctms_backend.user.dto;

/** Carries the generated temporary password back to the admin exactly once -- it is never
 * retrievable again afterwards (only the hash is persisted), mirroring the existing Patient
 * Portal account-creation response shape ({@code PortalAccountResponse}). */
public record CreateUserResponse(UserResponse user, String temporaryPassword) {}
