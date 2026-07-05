package com.ctms.ctms_backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Backs the password-history policy (cannot reuse the last N passwords). */
@Entity
@Table(name = "password_history")
@Getter
@Setter
@NoArgsConstructor
public class PasswordHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PasswordHistoryEntry(User user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
