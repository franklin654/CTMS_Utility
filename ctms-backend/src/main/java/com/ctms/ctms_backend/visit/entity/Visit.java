package com.ctms.ctms_backend.visit.entity;

import com.ctms.ctms_backend.subject.entity.Subject;
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
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A subject-specific visit -- either generated from a {@link VisitTemplate} at enrollment (see
 * VisitSchedulingService, re-synced in place while still SCHEDULED whenever the source template is
 * edited) or an ad-hoc visit requested directly for a subject with no template ({@link
 * #visitTemplate} null -- see VisitService#scheduleAdHoc). Rescheduling never mutates this row's
 * date -- it marks this row RESCHEDULED and creates a new linked SCHEDULED row (see
 * rescheduledFromVisit), so the reschedule trail (BRD Story 04 AC3) is reconstructable without a
 * separate history table. */
@Entity
@Table(name = "visit")
@Getter
@Setter
@NoArgsConstructor
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_template_id")
    private VisitTemplate visitTemplate;

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

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status = VisitStatus.SCHEDULED;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Column(name = "actual_time")
    private LocalTime actualTime;

    @Column(length = 2000)
    private String notes;

    @Column(name = "reason_code", length = 2000)
    private String reasonCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_visit_id")
    private Visit rescheduledFromVisit;

    @Column(name = "completed_at")
    private Instant completedAt;

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
