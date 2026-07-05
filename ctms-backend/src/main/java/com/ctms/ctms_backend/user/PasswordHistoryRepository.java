package com.ctms.ctms_backend.user;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, Long> {

    List<PasswordHistoryEntry> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
