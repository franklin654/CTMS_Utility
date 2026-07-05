package com.ctms.ctms_backend.visit.entity;

import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "visit_template")
@Getter
@Setter
@NoArgsConstructor
public class VisitTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "target_day", nullable = false)
    private Integer targetDay;

    @Column(name = "window_early_days", nullable = false)
    private Integer windowEarlyDays;

    @Column(name = "window_late_days", nullable = false)
    private Integer windowLateDays;

    @Column(name = "required_procedures", length = 2000)
    private String requiredProcedures;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    private VisitType visitType;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modified_by", nullable = false)
    private User modifiedBy;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.modifiedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.modifiedAt = Instant.now();
    }
}
