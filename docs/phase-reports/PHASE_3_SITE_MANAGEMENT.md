# Phase 3 — Site Management: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 2 (Stories 01–04): register a site, track activation status via a fixed 5-item prerequisite checklist, enforce those prerequisites before activation, view site information.

---

## 1. What Was Built

### Backend (`com.ctms.ctms_backend.site`)

- **Schema** (`V5__site_management.sql`): `site` table (study FK, metadata, PI/contact fields, `feasibility_status`, lifecycle `status`, `activation_date`, nullable `assigned_cra_id`) and `site_activation_checklist_item` (one row per site per fixed checklist item type, unique on `(site_id, item_type)`).
- **State machine**: two-state `PENDING_ACTIVATION → ACTIVE`, with no manual transition endpoint — the only path to `ACTIVE` is automatic (all 5 checklist items complete) or an explicit `attempt-activation` action, both funneling through a single `promote()` method so there's exactly one way a site becomes active.
- **Fixed 5-item checklist** (`ChecklistItemType` enum, not a config table, since the BRD names an exact fixed list with no per-study variation): Feasibility Completion, IRB/EC Approval, Contract Completion, Essential Documents Submission, Site Initiation Visit. Seeded automatically (all `PENDING`) at registration time.
- **`SiteService`**: registration (duplicate-code check, study resolution, checklist seeding), update, CRA assignment (validates the target user actually holds `CRA_MONITOR`), get/list.
- **`SiteActivationService`**: `updateChecklistItem` (silent auto-recheck-and-promote on every update — no error if still incomplete, since that's "making progress," not "activating"); `attemptActivation` (Story 03's explicit action — always audit-logs the attempt itself, win or lose; throws `SiteActivationBlockedException` carrying the exact missing-item labels if incomplete).
- **RBAC**: write endpoints (`register`, `update`, `assignCra`, checklist updates, attempt-activation) restricted to `STUDY_MANAGER`/`ADMIN` (matches the literal "As a Study Manager" actor on 3 of 4 stories); read open to all internal roles except `PATIENT_SUBJECT` (mirrors Phase 1's Study read-RBAC precedent — Story 04's actor is generic "As a User").
- **Audit + notifications**: every create/update/checklist-change/activation-attempt/promotion writes to the shared `AuditLog`; site activation notifies the site's creator (stand-in for "Study Manager") always, and the assigned CRA if one exists — skipped silently (not an error) if no CRA is assigned yet, since the BRD doesn't describe a CRA-assignment workflow at registration time.
- **5 new exceptions** wired into the existing single `GlobalExceptionHandler`, including a structured `SiteActivationBlockedException` handler that returns the missing-item list as a real JSON array (not just embedded in a message string), so the frontend can render it directly.

### Frontend (`features/sites/`)

- `site-list` (search + pagination, status chip), `site-create` (registration form), `site-detail` (metadata, activation checklist widget with green/red status per item and inline mark-complete-with-note+date, "Attempt Activation" button, CRA assignment).
- `StudyService` HTTP client pattern (`core/sites/site.service.ts`), wired into `app.routes.ts` and the shell nav, following Phase 1/2's established conventions exactly (lazy-loaded standalone components, `FormGroup`/`FormControl` built directly rather than via `FormBuilder`, `*appHasRole` for UX gating backed by server-side `@PreAuthorize`).
- **CRA assignment UX** (added after initial browser walkthrough feedback): a small new `GET /api/users?role=CRA_MONITOR&search=` lookup endpoint (gated to the same `STUDY_MANAGER`/`ADMIN` roles that can assign a CRA — not a general user directory), wired into a Material autocomplete on the site-detail page so users don't need to remember exact usernames. Snackbar feedback added for both successful CRA assignment and site activation.

## 2. Defects Found & Fixed

1. **`GET /api/sites` 500 error — `lower(bytea)` type inference.** The repository's JPQL search query used `:search is null or lower(...)` to make the search term optional. When `:search` was bound as `null`, PostgreSQL's JDBC driver couldn't infer its type from context and defaulted to `bytea`, breaking the `lower()` call on every list request. Found during your browser walkthrough (reported as a 500 on the sites list after a CRA-related action, but the actual failing request was the page's own site list). Fixed by always passing a non-null empty string for "no search" instead of `null`, and changing the query condition to `:search = ''`.
2. **`assignCra` failure surfaced as a raw 500.** An invalid/non-CRA user ID threw a plain `IllegalArgumentException`, which isn't wired into `GlobalExceptionHandler` and fell through to the generic 500 handler. Fixed by introducing a dedicated `InvalidCraAssignmentException` (400) following the same pattern as every other domain exception in this phase.
3. **CRA assignment UX gap.** The initial implementation required typing a raw numeric user ID, which is impractical since CRAs aren't identified by ID anywhere else in the UI. Fixed by switching `AssignCraRequest` to accept a `craUsername` string, and adding the `/api/users` lookup endpoint + Material autocomplete described above.

## 3. Known Gap Discovered (Deferred — Belongs to Phase 2, Not This Phase)

While testing document approval as `sysadmin`, you hit a 403 that should not happen: `DocumentWorkflowService.assertRoleForStage` performs a **purely role-based check** against the `document_workflow_role` config table, which only lists `STUDY_MANAGER` (review) and `QA_COMPLIANCE_AUDITOR` (approval) — it has no `ADMIN` entry. Meanwhile the controller's coarse `@PreAuthorize` explicitly includes `ADMIN` in `WORKFLOW_ROLES`, implying an administrator should be able to act as reviewer/approver. The result: an admin passes the controller gate but fails the service-level check and gets a confusing 403. This is a **Phase 2 (Document Management) defect**, not a Phase 3 regression — logged here for traceability and deferred to be fixed in a follow-up, per your explicit instruction to keep this phase's report scoped to Site Management.

Separately, `DocumentWorkflowService` also has **no segregation-of-duties guard** — a user who holds both the upload role and the configured reviewer/approver role for a category can currently review/approve/reject their own uploaded document version. Nothing in the BRD's Epic 7 acceptance criteria states this literally, but it's a standard GxP/21 CFR Part 11 expectation for a compliance system. You've asked to leave this as-is for now; also logged here for future consideration.

## 4. Verification

- **Unit tests** (19 cases, `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`): `SiteServiceTest` (5 cases — duplicate code, happy path, checklist seeding, CRA-assignment validation) and `SiteActivationServiceTest` (7 cases — auto-promotion with/without CRA notification, partial-completion non-promotion, exact missing-item list and ordering, idempotent re-activation, unknown item type).
- **Integration test** (1 case, `SiteManagementIntegrationTest`, `@SpringBootTest @Transactional` against the dedicated `ctms_testdb`): full lifecycle — register, assign CRA, mark 4/5 items complete, blocked activation attempt with the correct single missing label, complete the 5th item, confirm auto-activation with `activationDate` set, confirm `AuditLog` rows for create/checklist-updates/blocked-attempt/promotion, confirm `Notification` rows for both the creator and the assigned CRA.
- **Manual `curl` sequence** against real Postgres: register site, duplicate-code rejection (409), full checklist view, blocked early activation attempt (all 5 missing), mark 4 items complete, blocked attempt (exactly 1 missing), CRA assignment, complete last item → auto-activation confirmed via `GET`, full audit trail confirmed via `GET /api/audit-logs`, RBAC checks for `SITE_COORDINATOR` (write-no, 403) and `PATIENT_SUBJECT` (read-no, 403).
- **Browser walkthrough** with you: register → checklist mark-complete flow → blocked activation with missing-item list rendered → CRA assignment (including the autocomplete dropdown and snackbar added after initial feedback) → auto-activation. The two defects in §2 were caught this way, not by automated tests, since both were runtime-environment-specific (real Postgres type inference, real invalid-ID request shape).

## 5. Known Gaps / Carried-Forward Items

- Same Docker/Testcontainers gap as prior phases — integration tests use the dedicated `ctms_testdb` Postgres database instead.
- "Essential documents submission" is a manual checkbox, not live-linked to Phase 2's `Document` entity — no `site_id` was added to `Document` in this phase; that live linkage is better suited to Epic 9 (System Configuration)'s document-requirement mapping.
- No field-level role hiding on `SiteResponse` — nothing in the BRD names a specific sensitive field, mirroring the Phase 1 precedent.
- The two Document Management defects noted in §3 above are deferred, not fixed, per your explicit instruction.

## 6. Ready for Phase 4
