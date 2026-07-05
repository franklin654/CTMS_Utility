package com.ctms.ctms_backend.notification;

import com.ctms.ctms_backend.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadFalseOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadFalse(User recipient);

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    Optional<Notification> findByRecipientAndTypeAndLink(User recipient, String type, String link);

    List<Notification> findByLinkAndReadFalse(String link);

    @Query("""
            select n from Notification n
            where n.recipient = :recipient
              and (:type = '' or n.type = :type)
              and (:unreadOnly = false or n.read = false)
            order by n.createdAt desc
            """)
    Page<Notification> search(
            @Param("recipient") User recipient,
            @Param("type") String type,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);
}
