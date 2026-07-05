package com.ctms.ctms_backend.monitoring.entity;

import com.ctms.ctms_backend.site.entity.Site;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** BL Epic 6 Story 02 (not part of backlog v0.2 numbering used elsewhere, mapped from BRD Epic 6).
 * A log entry, not a lifecycle -- BRD says "log new monitoring visits" / "history displayed
 * chronologically", so visitDate is when the visit already happened, editable afterward with
 * standard audit UPDATE logging and no mandatory reason code (Monitoring Visit correction isn't
 * in CLAUDE.md's explicit reason-code list). */
@Entity
@Table(name = "monitoring_visit")
@Getter
@Setter
@NoArgsConstructor
public class MonitoringVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cra_id", nullable = false)
    private User cra;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    private MonitoringVisitType visitType;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(length = 4000)
    private String findings;

    @Column(name = "issues_identified", length = 2000)
    private String issuesIdentified;

    @Column(name = "checklist_notes", length = 2000)
    private String checklistNotes;

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
