package com.ctms.ctms_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Data-driven reviewer/approver role per (optional) category -- category null = default rule
 * applying to all categories. Read at call time by DocumentWorkflowService, not hardcoded. */
@Entity
@Table(name = "document_workflow_role")
@Getter
@Setter
@NoArgsConstructor
public class DocumentWorkflowRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStage stage;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;
}
