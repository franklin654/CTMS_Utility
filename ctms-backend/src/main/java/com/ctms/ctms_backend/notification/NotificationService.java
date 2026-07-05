package com.ctms.ctms_backend.notification;

import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            MailService mailService,
            ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.eventPublisher = eventPublisher;
    }

    /** Convenience for callers that would rather not depend on Spring's event bus directly. */
    public void notify(Long recipientUserId, String type, String title, String body, String link) {
        eventPublisher.publishEvent(new NotificationEvent(recipientUserId, type, title, body, link));
    }

    @EventListener
    @Transactional
    public void onNotificationEvent(NotificationEvent event) {
        User recipient = userRepository
                .findById(event.recipientUserId())
                .orElseThrow(() -> new NoSuchElementException("No user " + event.recipientUserId()));

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(event.type());
        notification.setTitle(event.title());
        notification.setBody(event.body());
        notification.setLink(event.link());
        notificationRepository.save(notification);

        mailService.send(recipient.getEmail(), event.title(), event.body() == null ? event.title() : event.body());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(User recipient, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientAndReadFalseOrderByCreatedAtDesc(recipient, pageable)
                : notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient, pageable);
        return page.map(NotificationResponse::from);
    }

    public long unreadCount(User recipient) {
        return notificationRepository.countByRecipientAndReadFalse(recipient);
    }

    @Transactional
    public void markRead(User recipient, Long notificationId) {
        notificationRepository.findByIdAndRecipient(notificationId, recipient).ifPresent(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                n.setReadAt(Instant.now());
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(User recipient) {
        Page<Notification> unread = notificationRepository.findByRecipientAndReadFalseOrderByCreatedAtDesc(
                recipient, Pageable.unpaged());
        Instant now = Instant.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
        });
        notificationRepository.saveAll(unread);
    }
}
