package com.ctms.ctms_backend.notification;

/**
 * Published by any module that wants to notify a user (tasks, visits, escalations, document
 * expiry, etc. in later phases), decoupled from how the notification is actually delivered.
 * {@link NotificationService} listens for these and handles in-app + email fan-out.
 */
public record NotificationEvent(Long recipientUserId, String type, String title, String body, String link) {}
