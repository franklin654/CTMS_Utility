package com.ctms.ctms_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Data-driven category -> role DENY rules (default-allow, explicit deny-list), per CLAUDE.md
 * 2.7 -- not hardcoded per-category Java conditionals. */
@Entity
@Table(name = "document_category_access_rule")
@Getter
@Setter
@NoArgsConstructor
public class DocumentCategoryAccessRule {

    public static final String ACCESS_DENY = "DENY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(nullable = false, length = 20)
    private String access = ACCESS_DENY;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
