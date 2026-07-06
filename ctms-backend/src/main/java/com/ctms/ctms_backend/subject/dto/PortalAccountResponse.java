package com.ctms.ctms_backend.subject.dto;

/** Deliberately carries the plaintext temporary password -- displayed once to the staff member who
 * triggered account creation/reset, for them to relay to the patient in person (this deployment's
 * network can't reliably deliver email/SMS). Never persisted or logged anywhere beyond this
 * one-time response and the audit trail's action record (which does not include the password). */
public record PortalAccountResponse(String username, String temporaryPassword) {}
