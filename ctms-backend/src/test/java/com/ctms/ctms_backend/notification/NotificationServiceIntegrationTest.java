package com.ctms.ctms_backend.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Runs against a real Postgres (via DB_URL/DB_USERNAME/DB_PASSWORD env vars), not Testcontainers,
 * since Docker isn't available in this environment. Rolled back after each test. */
@SpringBootTest
@Transactional
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void publishingAnEventCreatesAnInAppNotification() {
        User recipient = new User();
        recipient.setUsername("notif-test-user");
        recipient.setEmail("notif-test-user@ctms.local");
        recipient.setFullName("Notification Test User");
        recipient.setPasswordHash(passwordEncoder.encode("irrelevant-not-logged-in"));
        Role role = roleRepository.findByCode(Role.SITE_COORDINATOR).orElseThrow();
        recipient.setRoles(new HashSet<>(java.util.List.of(role)));
        recipient = userRepository.save(recipient);

        notificationService.notify(
                recipient.getId(), "TEST", "A test notification", "body text", "/somewhere");

        var page = notificationService.list(recipient, false, Pageable.ofSize(10));
        assertEquals(1, page.getContent().size());
        assertEquals("A test notification", page.getContent().get(0).title());
        assertEquals(1, notificationService.unreadCount(recipient));

        notificationService.markRead(recipient, page.getContent().get(0).id());
        assertEquals(0, notificationService.unreadCount(recipient));
    }
}
