package com.ctms.ctms_backend.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id, String type, String title, String body, String link, boolean read, Instant createdAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
