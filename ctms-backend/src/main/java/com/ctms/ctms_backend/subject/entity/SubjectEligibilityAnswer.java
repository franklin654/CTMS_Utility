package com.ctms.ctms_backend.subject.entity;

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

/** Append-only per-criterion answer captured at enrollment time -- immutable per ALCOA, even
 * though it doesn't affect any later state (the eligibility decision is made once, at enrollment). */
@Entity
@Table(name = "subject_eligibility_answer")
@Getter
@Setter
@NoArgsConstructor
public class SubjectEligibilityAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private EligibilityCriterion criterion;

    @Column(nullable = false)
    private boolean met;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @PrePersist
    void onCreate() {
        this.recordedAt = Instant.now();
    }
}
