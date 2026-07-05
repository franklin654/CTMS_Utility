package com.ctms.ctms_backend.esignature;

import com.ctms.ctms_backend.user.User;
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

/**
 * 21 CFR Part 11 e-signature primitive: password re-authentication (no second factor, per org
 * constraint -- see Implementation Plan §1) plus mandatory reason-for-signing, immutably recorded
 * against the signed entity. Once created, a row is never updated or deleted.
 */
@Entity
@Table(name = "e_signature")
@Getter
@Setter
@NoArgsConstructor
public class ESignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entity_name", nullable = false, length = 255)
    private String entityName;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    public ESignature(User user, String entityName, String entityId, String reason) {
        this.user = user;
        this.entityName = entityName;
        this.entityId = entityId;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        this.signedAt = Instant.now();
    }
}
