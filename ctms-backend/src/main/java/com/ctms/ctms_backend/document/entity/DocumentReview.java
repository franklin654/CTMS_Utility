package com.ctms.ctms_backend.document.entity;

import com.ctms.ctms_backend.document.DocumentVersion;
import com.ctms.ctms_backend.esignature.ESignature;
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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One immutable row per reviewer/approver action -- mirrors study.entity.StudyStatusHistory.
 * `esignature` is populated only for stage=APPROVAL, action=APPROVED. */
@Entity
@Table(name = "document_review")
@Getter
@Setter
@NoArgsConstructor
public class DocumentReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(length = 2000)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acted_by", nullable = false)
    private User actedBy;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "esignature_id")
    private ESignature esignature;

    @PrePersist
    void onCreate() {
        this.actedAt = Instant.now();
    }
}
