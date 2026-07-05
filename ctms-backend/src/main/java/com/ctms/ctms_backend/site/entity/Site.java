package com.ctms.ctms_backend.site.entity;

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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "site")
@Getter
@Setter
@NoArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @Column(name = "site_code", nullable = false, unique = true, length = 30)
    private String siteCode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "principal_investigator_name", nullable = false, length = 255)
    private String principalInvestigatorName;

    @Column(name = "principal_investigator_contact", nullable = false, length = 255)
    private String principalInvestigatorContact;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 50)
    private String contactPhone;

    @Column(name = "feasibility_status", nullable = false, length = 50)
    private String feasibilityStatus;

    @Column(name = "regulatory_information", length = 2000)
    private String regulatoryInformation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SiteStatus status = SiteStatus.PENDING_ACTIVATION;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_cra_id")
    private User assignedCra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backup_cra_id")
    private User backupCra;

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
