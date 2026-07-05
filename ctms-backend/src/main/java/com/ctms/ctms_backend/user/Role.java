package com.ctms.ctms_backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RBAC role, per BRD §4 / consolidated role list. Seeded by V1 migration. */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Role {

    /** Canonical role codes, matching the values seeded in V1__phase0_platform_foundation.sql. */
    public static final String ADMIN = "ADMIN";
    public static final String STUDY_MANAGER = "STUDY_MANAGER";
    public static final String SITE_COORDINATOR = "SITE_COORDINATOR";
    public static final String INVESTIGATOR = "INVESTIGATOR";
    public static final String CRA_MONITOR = "CRA_MONITOR";
    public static final String DATA_MANAGEMENT = "DATA_MANAGEMENT";
    public static final String FINANCE_MANAGER = "FINANCE_MANAGER";
    public static final String QA_COMPLIANCE_AUDITOR = "QA_COMPLIANCE_AUDITOR";
    public static final String CLINICAL_LEADERSHIP = "CLINICAL_LEADERSHIP";
    public static final String EXECUTIVE = "EXECUTIVE";
    public static final String SPONSOR_CRO_LEADERSHIP = "SPONSOR_CRO_LEADERSHIP";
    public static final String PATIENT_SUBJECT = "PATIENT_SUBJECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(length = 255)
    private String description;
}
