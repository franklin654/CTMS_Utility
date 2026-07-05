package com.ctms.ctms_backend.rules;

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

/** One immutable, versioned snapshot of DRL content for a {@link RuleSet}. Only {@code active} is
 * ever mutated (when a newer version is activated in its place). */
@Entity
@Table(name = "rule_definition")
@Getter
@Setter
@NoArgsConstructor
public class RuleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @Column(nullable = false)
    private int version;

    @Column(name = "drl_content", columnDefinition = "TEXT")
    private String drlContent;

    @Column(nullable = false)
    private boolean active = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
