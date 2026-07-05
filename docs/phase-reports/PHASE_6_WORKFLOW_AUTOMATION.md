# Phase 6 — Workflow & Automation: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 5 (Stories 01–04): auto-create tasks from lifecycle events, SLA-based task tracking, automatic task escalation, and consolidated notifications for tasks/visits/delays — explicitly the "automation backbone" the implementation plan calls out for reuse by later phases (AE escalation, milestone alerts, document-expiry reminders).

---

## 1. What Was Built

### Backend (`com.ctms.ctms_backend.task`)

- **Schema** (`V9__task_management.sql`): `task` table (event code, title/description, `entity_name`/`entity_id` link back to the source record, owner + escalation-target FKs with their descriptive role labels, priority, status, `due_at`, `escalated`/`escalated_at` overlay columns). Seeds a shared **`TASK_RULES_DEFAULT`** Drools rule set (category `WORKFLOW`, seeded but unused since Phase 0) with one rule per trigger event.
- **Auto-creation is config-driven, not hardcoded**: `TaskRuleService.evaluate(eventCode)` fires the Drools rule set and returns SLA hours, descriptive owner/escalation role labels, and priority for that event — adding a new trigger event later means adding a DRL rule, not a new code branch. This mirrors Phase 4's `ELIGIBILITY_DEFAULT` hybrid pattern exactly (a config table would tell you *what*; Drools tells you *how*).
- **Actual owner/escalation-target resolution happens at the call site, not in Drools**: the event listeners (`SubjectService.enrollSubject`, `SiteActivationService.promote`, `VisitService.markMissed`) resolve real `User`s using domain relationships already in scope (e.g. `site.getAssignedCra()` or `subject.getCreatedBy()` for owner; `study.getCreatedBy()` for escalation target) and pass the resolved IDs into `TaskService.createTask`. Drools only supplies the flat SLA/priority/role-label lookup — a deliberate boundary so the rule engine isn't asked to do relationship traversal it shouldn't own.
- **Three trigger events wired up this phase** (the only ones the BRD names explicitly — "subject enrollment or site activation", "missed visit"; "doc expiry" excluded since `Document` has no expiry-date field):
  1. `SUBJECT_ENROLLED` — fires after `VisitSchedulingService.generateForSubject` in `SubjectService.enrollSubject`.
  2. `SITE_ACTIVATED` — fires from the shared `promote()` method in `SiteActivationService` (both the silent auto-promotion and explicit-attempt paths funnel through it), **only if no CRA is assigned yet** — avoids a noisy no-op task when a CRA was already assigned before activation.
  3. `VISIT_MISSED` — fires from `VisitService.markMissed`.
- **Task status**: `OPEN → IN_PROGRESS → COMPLETED`, with escalation as an **overlay** (`escalated` boolean + `escalatedAt`) rather than a status value — a task can be actively `IN_PROGRESS` and simultaneously flagged escalated, since Story 03's "escalation includes updating task status" doesn't require collapsing the work-tracking states into the escalation state.
- **`TaskEscalationService`** — an hourly `@Scheduled` sweep (not daily like Phase 5's visit-alert job, since Task SLAs are measured in hours) that finds `OPEN`/`IN_PROGRESS` tasks past `dueAt` and not yet escalated, reassigns ownership to the resolved escalation target (confirmed design: "may be reassigned" defaults to always-reassign), notifies both the original owner and the new owner, and audit-logs the reassignment. Escalation is single-level — no BRD mention of repeated escalation, so `escalated` is a one-way flag.
- **RBAC**: task read ("my tasks") → all internal roles except `PATIENT_SUBJECT`; oversight "all tasks" view → `STUDY_MANAGER`/`ADMIN` only; start/complete → the task's current owner or `ADMIN` (enforced in the service layer, not just `@PreAuthorize`, mirroring how ownership checks work elsewhere in this codebase).
- **Notification center gets its first full history+filter page** — previously only a bell-dropdown widget existed. `NotificationRepository`/`NotificationService`/`NotificationController` gained a `type` filter following the established `:type = '' or n.type = :type` JPQL pattern (same convention as `SubjectRepository.search`).
- **Audit + notifications**: every task create/state-change/escalation writes to `AuditLog`; `TASK_ASSIGNED` fires on creation, `TASK_ESCALATED` fires (to both parties) on breach.

### Frontend (`features/tasks/task-inbox/`, `features/notifications/notification-list/`)

- `task-inbox` — "My Tasks" / "All Tasks" (oversight toggle for `STUDY_MANAGER`/`ADMIN`) with client-computed urgency badges (green >48h, amber <48h, red overdue) and inline Start/Complete actions.
- `notification-list` — the first full notification history page: type filter dropdown, unread-only toggle, pagination, mark-read-on-click, mark-all-read.
- Nav links added to the shell for both.

## 2. Defects Found & Fixed

1. **`VisitService.markMissed` had no actor context** (design-time) — needed one to attribute the auto-created `VISIT_MISSED` task's `createdBy`. Fixed by threading `Principal`/`actorUsername` through the controller → service call, updating all existing call sites (unit tests, integration tests).
2. **Timezone mismatch during manual verification, not app code** — forcing a test SLA breach via raw `UPDATE task SET due_at = now() - interval '1 hour'` silently failed to trigger escalation, because Postgres's session `now()` is in `Asia/Kolkata` while Hibernate maps `Instant` columns assuming UTC (`hibernate.jdbc.time_zone=UTC`) — a 5.5-hour skew. Fixed the verification SQL to use `(now() AT TIME ZONE 'UTC')`; no application code was at fault, but worth documenting since it would bite anyone hand-editing timestamp columns in this DB directly.

No other runtime defects — the browser walkthrough confirmed everything working on the first pass, including the escalation-reassignment behavior (the original owner correctly loses the task from their list and gets a `TASK_ESCALATED` notification instead, rather than the task lingering in both lists).

## 3. Verification

- **Unit tests** (17 new): `TaskRuleServiceTest` (4 — one per trigger event's Drools outcome, plus unknown-event handling), `TaskServiceTest` (6 — `dueAt` computed from SLA hours, start/complete guarded transitions, owner-or-admin guard), `TaskEscalationServiceTest` (2 — breach detection, reassignment + dual notification), plus the existing `SubjectServiceTest`/`SiteActivationServiceTest`/`VisitServiceTest` updated for the new `TaskService` dependency.
- **Integration tests** (5, `TaskManagementIntegrationTest`, `@SpringBootTest @Transactional` against real `ctms_testdb`): subject enrollment auto-creates a task with the correct owner/escalation target; site activation with no CRA creates a task, with a CRA already assigned does not; visit-missed auto-creates a task; a forced SLA breach through the real escalation sweep reassigns ownership and notifies both parties.
- **Full backend suite**: 107 tests, 0 failures, 0 errors (was 90 after Phase 5).
- **Manual `curl` verification** against real Postgres: all three trigger events end-to-end, task lifecycle (start/complete) with the owner-or-admin guard, oversight-view RBAC, notification type filtering, and the real `@Scheduled` escalation sweep (temporarily shortened to a 10-second cron to observe it fire live against a genuinely breached task, then reverted) confirming reassignment, dual notification, and no double-escalation on a repeat sweep.
- **Browser walkthrough** with you: task inbox (My Tasks / All Tasks toggle, urgency badges, Start/Complete), the escalated task correctly disappearing from the original owner's list and appearing under the new owner, notification history page (type filter, unread-only), and `PATIENT_SUBJECT` correctly missing the Tasks nav link — all confirmed working, no defects found during this pass.

## 4. Known Gaps / Carried-Forward Items

- **Only 3 trigger events wired up** — the BRD gives no exhaustive list; "doc expiry" (mentioned in the Epic IPO notes) is explicitly out of scope since `Document` has no expiry-date field yet. Adding more trigger events later is a matter of a new DRL rule in `TASK_RULES_DEFAULT` plus a new event-listener call site — the automation backbone this phase builds does not need rework to extend.
- **Escalation is single-level only** — no repeated/multi-level escalation exists (e.g., escalating again if the escalation target also lets it breach), since the BRD gives no indication this is expected. This is the same "single-level" scope call as Phase 5's visit alerts.
- **No `TaskEscalationHistory` table** — `AuditLog` plus the `escalated`/`escalatedAt` overlay is sufficient lineage, mirroring the same reasoning as Visit's lack of a separate status-history table in Phase 5.
- **RBAC/oversight scoping is global, not per-study** — the "All Tasks" oversight view for `STUDY_MANAGER`/`ADMIN` shows every task system-wide rather than scoped to studies that Study Manager manages. The BRD gives no per-study task-visibility requirement, and this matches the same global-visibility precedent used elsewhere (e.g. the audit log).
- Same Docker/Testcontainers gap as prior phases — integration tests use the dedicated `ctms_testdb` Postgres database instead.

## 5. Ready for Phase 7
