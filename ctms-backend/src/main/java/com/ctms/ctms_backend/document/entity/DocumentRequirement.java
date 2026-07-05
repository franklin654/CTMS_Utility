package com.ctms.ctms_backend.document.entity;

import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.entity.StudyStatus;
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

/** BL Epic 9 Story 03. Maps a per-study mandatory document category to a Study lifecycle phase
 * (studyPhase restricted to ACTIVE/CONDUCT/CLOSEOUT at the service layer -- "Start-Up"/"Conduct"/
 * "Closeout" in the BRD's own wording). Consumed by StudyService.transition as a blocking guard
 * before DRAFT->ACTIVE, ACTIVE->CONDUCT, CONDUCT->CLOSEOUT. Per-study, mirroring
 * EligibilityCriterion's existing per-study configuration pattern -- different studies can have
 * different mandatory-document sets. documentCategory is free text matching Document.category's
 * existing type, not a new enum. */
@Entity
@Table(name = "document_requirement")
@Getter
@Setter
@NoArgsConstructor
public class DocumentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_phase", nullable = false, length = 20)
    private StudyStatus studyPhase;

    @Column(name = "document_category", nullable = false, length = 100)
    private String documentCategory;

    @Column(nullable = false)
    private boolean mandatory = true;

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
