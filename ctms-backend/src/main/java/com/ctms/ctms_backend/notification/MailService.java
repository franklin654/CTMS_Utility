package com.ctms.ctms_backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Spring Mail (self-hosted/internal SMTP only -- no third-party transactional
 * email API, per org constraint). When {@code notification.email.enabled=false} (the default until
 * a real internal SMTP relay is configured), sends are logged instead of dispatched, so the rest of
 * the system can be built and exercised without a mail server. Used directly by the auth flow for
 * password-reset emails, and reused by the broader Notification framework (Phase 0 exit item) for
 * the email channel of in-app/event-driven notifications.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String fromAddress;

    public MailService(
            JavaMailSender mailSender,
            @Value("${notification.email.enabled}") boolean emailEnabled,
            @Value("${notification.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromAddress = fromAddress;
    }

    public void send(String toAddress, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email delivery disabled (notification.email.enabled=false); would have sent to={} subject={}", toAddress, subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
