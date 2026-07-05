package com.ctms.ctms_backend.adverseevent.entity;

import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.visit.entity.Visit;
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

/** BL-08 (not part of backlog v0.2, reintroduced per Phase 7 scope decision). Status workflow
 * OPEN -> UNDER_REVIEW -> RESOLVED (guarded, see AdverseEventService) -- RESOLVED is only ever
 * reached via the dedicated `resolve` action requiring resolutionNotes, mirroring Subject
 * withdrawal / Study closeout's "dedicated action for a terminal/compliance-sensitive transition"
 * pattern. SEVERE/LIFE_THREATENING severities auto-create an escalation Task via Phase 6's engine
 * (see AdverseEventService#report). visit is optional -- AEs are commonly reported between
 * scheduled visits, unlike TestResult which the BRD explicitly ties to a visit. */
@Entity
@Table(name = "adverse_event")
@Getter
@Setter
@NoArgsConstructor
public class AdverseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdverseEventSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdverseEventStatus status = AdverseEventStatus.OPEN;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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
