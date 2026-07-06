# Phase 13 — UAT, Integration Testing & Go-Live: Findings & Remediation Report

**Status:** All 4 code-level findings fixed and verified. 6 lower-severity items remain documented/accepted (no code change).
**Scope decided with you:** Of the six workstreams the implementation plan lists for Phase 13 (regression, load testing, security/RBAC review, EDC integration test pass, training materials/runbook, go-live cutover), only **regression + RBAC/security audit** was selected. Load testing, EDC integration, and documentation deliverables (training materials, admin runbook, cutover plan) are out of scope for this phase.

This document originally captured findings from three parallel research passes with remediation deferred. All 4 items marked "Fix" below have since been implemented, tested, and manually verified against real Postgres. A fifth, unrelated defect (subject-linked document upload/view was never wired end-to-end) surfaced separately while dry-running the Phase 12 demo walkthrough and was fixed in the same session — it's documented in §5 since it didn't come from the original 3-fork audit.

---

## 1. RBAC Matrix Audit

**Canonical source of truth**: `Role.java` + `V1__phase0_platform_foundation.sql` (lines 163–173), which exactly reproduce the Implementation Plan's §2 "RBAC Roles" table. 12 canonical roles: `ADMIN, STUDY_MANAGER, SITE_COORDINATOR, INVESTIGATOR, CRA_MONITOR, DATA_MANAGEMENT, FINANCE_MANAGER, QA_COMPLIANCE_AUDITOR, CLINICAL_LEADERSHIP, EXECUTIVE, SPONSOR_CRO_LEADERSHIP, PATIENT_SUBJECT`. No role-name typos or ad hoc strings found anywhere across all 30 controllers (100 `@PreAuthorize` annotations checked) — every literal matches the canonical list exactly.

### HIGH — `ESignatureController` has zero `@PreAuthorize`
`POST /api/e-signatures` (sign) and `GET /api/e-signatures` (history) have no role restriction at all. Since the global security filter only requires `.authenticated()`, **any authenticated user of any role — including `PATIENT_SUBJECT` — can currently capture a 21 CFR Part 11 e-signature against an arbitrary entity (e.g. someone else's Study closeout or Document approval), or read the signature history for any entity/id in the system.** This directly conflicts with the Implementation Plan's stated intent of RBAC on all endpoints from day one, and is the most significant finding in this audit given e-signatures are the Part 11 mechanism itself.

**Fixed:** class-level `@PreAuthorize` added to `ESignatureController` with the broad staff-only role set (excludes `PATIENT_SUBJECT`), matching `DocumentController.READ_ROLES`.
**File:** `ctms-backend/src/main/java/com/ctms/ctms_backend/esignature/ESignatureController.java`
**Verified:** live curl against `ctms_db` — a `PATIENT_SUBJECT`-role user now gets `403` on both `GET` and `POST /api/e-signatures`; a `STUDY_MANAGER` still gets `200`.

### LOW/INFORMATIONAL — `NotificationController` has no `@PreAuthorize`
All 4 endpoints are internally scoped to `currentUser(principal)`, so this is functionally safe — no user can see/mark another user's notifications regardless of role. Flagged only because every other controller in the codebase has an explicit RBAC statement; this one's safety currently relies on implicit self-scoping rather than a stated role policy.
**Agreed remediation:** Documented as a finding for sign-off; no code change planned (per "fix the clear-cut ones, document the rest").

### MEDIUM — Documented, deliberate deviations (no action needed)
`AdverseEventController.REPORT_ROLES` and `TestResultController.WRITE_ROLES` both grant `SITE_COORDINATOR` write/record access, where the backlog's literal user-story actor is "doctor" (Investigator). This is already implemented with an explicit rationale in code comments ("Site Coordinator can record but not review — reviewing implies clinical judgment"), and the actual clinical-judgment actions (`transition`, `resolve`, `review`) are correctly gated to `INVESTIGATOR/STUDY_MANAGER/ADMIN` only. Flagging for the record/sign-off, not proposing a change — this was already a considered Phase 7 design decision.

### LOW — Narrower-than-usual read access on two controllers
`EligibilityCriterionController.GET /` and `VisitTemplateController.GET /` restrict reads to `STUDY_MANAGER, SITE_COORDINATOR, ADMIN` only, unlike the broad `READ_ROLES` pattern (which additionally includes `INVESTIGATOR, CRA_MONITOR, DATA_MANAGEMENT, FINANCE_MANAGER, QA_COMPLIANCE_AUDITOR, CLINICAL_LEADERSHIP, EXECUTIVE, SPONSOR_CRO_LEADERSHIP`) used almost everywhere else. No canonical-matrix language clearly justifies excluding e.g. `QA_COMPLIANCE_AUDITOR` from viewing eligibility criteria or visit templates (both are read-only reference/config data relevant to monitoring/audit roles).
**Agreed remediation:** Documented as a finding for sign-off; no code change planned this round.

### LOW — Document category-level RBAC lives outside `@PreAuthorize` (not a gap, but a scoping note)
`DocumentController`'s broad `READ_ROLES` uniquely includes `PATIENT_SUBJECT`, which looks over-broad at first glance — but `DocumentAccessControlService.assertReadable()` applies a second, data-driven deny-list layer per document category (seeded in `V4__document_approval_workflow.sql`), correctly denying `PATIENT_SUBJECT` from `PRINCIPAL_INVESTIGATOR_CV`/`FINANCIAL` categories and `CRA_MONITOR` from `FINANCIAL` — matching the BRD's literal example exactly. Noted so a future `@PreAuthorize`-only audit doesn't misreport this controller as over-permissive. No action needed.

### INFORMATIONAL — Budget/Payment visibility excludes QA/Leadership roles
`BudgetController` and `PaymentController` restrict every endpoint (including read/export) to `FINANCE_MANAGER, ADMIN` only — neither `QA_COMPLIANCE_AUDITOR` (canonical mandate: "traceability reports... validation views") nor `CLINICAL_LEADERSHIP`/`EXECUTIVE` (canonical mandate: "portfolio dashboards... risk visibility" / "cross-study analytics") have read access to budget/payment data.
**Agreed remediation:** Documented as a finding for sign-off; no code change planned this round (may be intentional — financial data access is often deliberately narrow even for adjacent oversight roles).

---

## 2. Audit-Log Completeness Audit

**Reference confirmed:** `AuditLog`/`AuditLogRepository` are strictly insert-only — no update/delete method exists or is called anywhere. This invariant (CLAUDE.md §2.3's "immutable, no update/delete endpoint") holds cleanly.

**Hard-delete check (CLAUDE.md §2.4):** Confirmed only one hard-delete path exists in the entire codebase: `RefreshTokenRepository.deleteByUser(user)`, called from `AuthenticationService.changePassword`/`resetPassword` to invalidate all of a user's active sessions after a password change. This is a non-clinical, session-bookkeeping hard delete (not a trial-data entity), and a defensible security practice (force logout everywhere after password change) — but it is currently unaudited.

### HIGH — `AuthenticationService` has zero audit calls outside `login()`
`login()` is correctly audited (`AuditAction.LOGIN`/`LOGIN_FAILED`). But these five other state-changing methods have **no audit call at all**, despite mutating passwords, sessions, and tokens — a direct compliance gap for a system requiring GCP/21 CFR Part 11-style traceability:

| Method | What it mutates, unaudited |
|---|---|
| `refresh(String rawRefreshToken)` | Revokes old `RefreshToken`, issues + persists a new one |
| `logout(String rawRefreshToken)` | Revokes a `RefreshToken` |
| `changePassword(String username, ...)` | Mutates `User` password hash/expiry/lockout fields, writes a `PasswordHistoryEntry`, hard-deletes all refresh tokens for the user |
| `requestPasswordReset(String email)` | Creates and persists a `PasswordResetToken` |
| `resetPassword(String rawToken, String newPassword)` | Mutates `User` password (same as above), marks reset token used, hard-deletes all refresh tokens |

**Fixed:** all 5 methods now call `auditService.record(...)`. `changePassword`/`resetPassword` each note the session-invalidation side effect (the `RefreshTokenRepository.deleteByUser` hard-delete below) in the same entry rather than a separate row, since it's part of one atomic user action.
**File:** `ctms-backend/src/main/java/com/ctms/ctms_backend/security/AuthenticationService.java`
**Tests:** new `AuthenticationServiceTest` (8 cases — this service had zero unit tests before) — each method's audit call verified, plus negative cases (`refresh`/`logout` with an unknown token, `requestPasswordReset` with an unknown email) proving no audit entry is written when there's no real `User` to attach one to.
**Verified:** live curl against `ctms_db` — confirmed real `audit_log` rows for `refresh` ("access token refreshed"), `logout` ("session revoked (logout)"), `changePassword` ("password changed; all active sessions invalidated"), and `requestPasswordReset` ("password reset requested").

### MEDIUM — `VisitTemplateService.propagateToScheduledVisits` unaudited
`update()` (line 94) correctly audits the `VisitTemplate` entity itself (`AuditAction.UPDATE`, "template fields updated"), but the bulk mutation it triggers on every still-`SCHEDULED` `Visit` under that template — including a **recomputed `scheduledDate`** — has no audit entry of its own. Since visit-schedule adherence is compliance-relevant, this is a real (if narrow) gap: the audit trail shows the template changed, but not that N visits' dates moved as a result.
**Fixed:** `propagateToScheduledVisits` now records a per-`Visit` `UPDATE` audit entry (before/after `scheduledDate`, reason naming the driving `VisitTemplate`) for every visit it bulk-mutates — one row per affected visit, not a single batch summary, so each rescheduled visit is individually traceable like every other per-entity mutation in the codebase.
**File:** `ctms-backend/src/main/java/com/ctms/ctms_backend/visit/service/VisitTemplateService.java` (method `propagateToScheduledVisits`, called from `update`)
**Tests:** extended `VisitTemplateServiceTest.update_propagatesToScheduledVisitsOnly` to assert the new audit call fires with the correct before/after dates.
**Verified:** live curl against `ctms_db` — updating a template's `targetDay` produced a `Visit` `UPDATE` audit row (`before_value`/`after_value` = old/new `scheduledDate`) for the one affected scheduled visit.

### LOW/JUDGMENT CALL — `NotificationService` has 4 unaudited methods
`onNotificationEvent`, `markRead`, `markAllRead`, `clearByLink` all mutate `Notification` rows with no audit call. Arguably out of clinical/regulatory scope — in-app notification read-state isn't a clinical or business entity in the BRD's sense.
**Agreed remediation:** Documented as a finding for sign-off; no code change planned this round.

### LOW — `DocumentService.promoteToCurrent` doesn't separately audit the archived predecessor
Archives the previous `CURRENT` `DocumentVersion` (`status → ARCHIVED`) as a side effect of promoting a new version. In practice this is always called from `DocumentWorkflowService.approverFinalDecide`, which does log a `STATE_CHANGE` for the parent `Document` right after — so the *document's* transition is captured, just not the specific version-archival as its own line item.
**Agreed remediation:** Documented as a finding for sign-off; no code change planned this round (mitigated in practice by the parent's own audit entry).

---

## 3. Regression Coverage Finding

**No test today exercises one continuous chain across most/all 12 feature areas.** Every existing integration test (13 `@SpringBootTest` classes, one roughly per phase) stays scoped to its own phase's feature area, re-creating its own Study/Site/Subject/Visit scaffolding from scratch each time purely to reach the object under test — none chain Document/Consent → Financial → Task → Regulatory-Compliance → Notification together with cross-cutting assertions in a single scenario.

Current suite (at the time of the audit): **216 tests, 0 failures** (confirmed fresh via `target/surefire-reports`), runtime ~20 seconds — cheap to extend or re-run.

**Fixed:** new `CrossPhaseEndToEndIntegrationTest` (`fullLifecycle_studyThroughTraceability_allPhasesInterlock`) — one continuous scenario, not per-phase isolated setup: Study create → `ACTIVE` → `CONDUCT`; Site register → checklist complete → auto-`ACTIVE` (asserts the CRA-assignment task auto-creates); Subject enrollment; Visit schedule → consent-gate block → subject-linked consent upload → visit complete; Protocol Deviation report; Adverse Event report (asserts the SEVERE-severity Drools escalation task auto-creates) → transition → resolve (wrong password rejected, then e-signed); Budget create → Drools-generated Payment from the completed visit → hold → release (wrong password rejected, then e-signed); and a closing Traceability Report check on the `AdverseEvent`, `Payment`, and `Visit` entities just acted on, proving the whole chain is reconstructible from each entity's own audit trail. This is a genuinely new artifact, not a rerun of existing per-phase tests.
**File:** `ctms-backend/src/test/java/com/ctms/ctms_backend/CrossPhaseEndToEndIntegrationTest.java`
**Verified:** passes standalone and as part of the full suite.

**Minor aside, not a Phase 13 item:** Phase 11's report says "was 164 pre-Phase-11," but Phase 10's own report ended at 184 — likely a typo in the Phase 11 report (should read "184"). Not urgent, just noting it in case anyone audits the phase-report trail later.

---

## 4. Summary

| # | Item | Severity | Status |
|---|---|---|---|
| 1 | `ESignatureController` no RBAC | HIGH | **Fixed** — broad staff-only `@PreAuthorize` added, verified live |
| 2 | `AuthenticationService` 5 unaudited methods | HIGH | **Fixed** — all 5 now audit, 8 new unit tests, verified live |
| 3 | `VisitTemplateService.propagateToScheduledVisits` unaudited | MEDIUM | **Fixed** — per-visit audit entry added, verified live |
| 4 | No cross-phase E2E test exists | — | **Fixed** — `CrossPhaseEndToEndIntegrationTest` added |
| 5 | `NotificationController` no explicit RBAC (functionally safe) | LOW | Documented only, no change |
| 6 | AE/TestResult SITE_COORDINATOR grant vs. literal backlog actor | MEDIUM | Documented only (deliberate Phase 7 decision) |
| 7 | Two controllers with narrower-than-usual read RBAC | LOW | Documented only, no change |
| 8 | Budget/Payment excludes QA/Leadership read access | INFO | Documented only, no change |
| 9 | `NotificationService` 4 unaudited methods | LOW | Documented only, no change |
| 10 | `DocumentService.promoteToCurrent` version-archival not separately audited | LOW | Documented only, no change |
| 11 | `RefreshTokenRepository.deleteByUser` hard-delete, unaudited | — | **Fixed** as part of #2 (changePassword/resetPassword audit entries now note the session invalidation) |

Items 5, 6, 7, 8, 9, 10 remain intentionally undone — each has a documented rationale above for why no change was made, not a silent gap.

---

## 5. Separately Found & Fixed: Subject-Linked Document Upload/View Gap

Not part of the original 3-fork audit — surfaced while dry-running the Phase 12 demo walkthrough script. `Document.subject` (the FK added in Phase 12 for the consent gate) was fully supported end-to-end on the backend's write path (`DocumentService.createDocument`/`DocumentController.upload` both already accepted an optional `subjectId`), but:

- `DocumentResponse` never returned `subjectId`/`subjectCode` in any response.
- No endpoint existed to list a subject's documents (only the study-wide, unfiltered `GET /api/documents`).
- The Angular upload form (`document-upload.component`) only had `title`/`category`/`studyId` fields — no way to attach a document to a subject at all through the UI.

The upload call itself never errored (`subjectId` being optional), so this was invisible in normal use — a document would upload fine and just silently link to nothing more specific than its study, meaning the consent gate could never actually be satisfied through the UI.

**Fixed:**
- `DocumentResponse` now returns `subjectId`/`subjectCode`.
- New `GET /api/documents/by-subject/{subjectId}` + `DocumentRepository.findBySubjectIdAndVisibleTo` + `DocumentService.listBySubject` (same category DENY-list filtering as every other document list).
- Frontend `DocumentService.create()` now sends `subjectId`; new `listBySubject()` method.
- New **Documents** card on `subject-detail.component` (upload form + list), mirroring the existing Protocol Deviations/Adverse Events sections.

**Tests:** new `listBySubject_returnsOnlyThatSubjectsDocuments` case in `RegulatoryComplianceIntegrationTest`, proving per-subject scoping (uploading for one subject never leaks into another's list).
**Verified:** live curl against `ctms_db` confirmed the full round-trip — upload with `subjectId` → `subjectId`/`subjectCode` present on both `GET /{id}` and `GET /by-subject/{id}` → the consent gate genuinely unblocks visit completion afterward.
**Files:** `DocumentResponse.java`, `DocumentRepository.java`, `DocumentService.java`, `DocumentController.java`, `document.service.ts`, `document-upload.component.ts`, `subject-detail.component.ts`/`.html`.

---

## 6. Final Verification

- Full backend suite: **226/226 passing** (was 216 at the time of the original audit; +8 `AuthenticationServiceTest`, +1 `CrossPhaseEndToEndIntegrationTest`, +1 `listBySubject` case).
- Frontend: `ng build` and `ng test` both pass.
- All 4 fixed findings plus the document-linking gap were independently verified via manual curl against real Postgres (`ctms_db`), not just unit/integration tests. All verification data cleaned up afterward.
