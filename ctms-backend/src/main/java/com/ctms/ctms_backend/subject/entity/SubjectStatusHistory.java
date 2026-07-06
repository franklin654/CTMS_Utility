package com.ctms.ctms_backend.subject.entity;

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

@Entity
@Table(name = "subject_status_history")
@Getter
@Setter
@NoArgsConstructor
public class SubjectStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private SubjectStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private SubjectStatus toStatus;

    @Column(name = "reason_code", length = 2000)
    private String reasonCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** BL Epic 11 Story 02 -- populated only for withdrawal, which requires password
     * re-authentication (mirrors StudyStatusHistory.esignature exactly). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "esignature_id")
    private ESignature esignature;

    @PrePersist
    void onCreate() {
        this.changedAt = Instant.now();
    }
}
