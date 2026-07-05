package com.ctms.ctms_backend.notification;

import com.ctms.ctms_backend.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadFalseOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadFalse(User recipient);

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    Optional<Notification> findByRecipientAndTypeAndLink(User recipient, String type, String link);

    List<Notification> findByLinkAndReadFalse(String link);
}
