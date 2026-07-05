package com.ctms.ctms_backend.task.entity;

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

/** An auto-generated unit of work (BRD Epic 5 Story 01) -- created by TaskService.createTask in
 * response to a trigger event (SUBJECT_ENROLLED / SITE_ACTIVATED / VISIT_MISSED), with SLA hours
 * and role labels sourced from the TASK_RULES_DEFAULT Drools rule set (TaskRuleService). Escalation
 * is an overlay on top of the OPEN/IN_PROGRESS/COMPLETED work-tracking states, not a status value
 * itself -- see TaskEscalationService. */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "owner_role", nullable = false, length = 50)
    private String ownerRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escalation_target_id", nullable = false)
    private User escalationTarget;

    @Column(name = "escalation_role", nullable = false, length = 50)
    private String escalationRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private boolean escalated = false;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

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
