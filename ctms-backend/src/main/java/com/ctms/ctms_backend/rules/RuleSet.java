package com.ctms.ctms_backend.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named, categorized bucket of {@link RuleDefinition} versions (e.g. "visit-window-validation",
 * category VISIT). This is the "no-code" configuration surface: Phases 3/5/6/10 read the currently
 * active {@link RuleDefinition} for a RuleSet instead of hardcoding logic per study.
 */
@Entity
@Table(name = "rule_set")
@Getter
@Setter
@NoArgsConstructor
public class RuleSet {

    public static final String CATEGORY_WORKFLOW = "WORKFLOW";
    public static final String CATEGORY_VISIT = "VISIT";
    public static final String CATEGORY_DOCUMENT = "DOCUMENT";
    public static final String CATEGORY_NOTIFICATION = "NOTIFICATION";
    public static final String CATEGORY_PAYMENT = "PAYMENT";
    public static final String CATEGORY_ELIGIBILITY = "ELIGIBILITY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
