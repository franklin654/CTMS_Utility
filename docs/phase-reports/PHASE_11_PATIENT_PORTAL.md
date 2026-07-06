# Phase 11 — Patient Portal: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 10 (Stories 01–05): secure patient login, visit schedule viewing, notifications, document upload, and profile editing — plus adverse-event self-reporting (per your scope decision, closing a gap Phase 7 explicitly deferred here).

---

## 1. Context

This phase is the largest architectural departure of the entire project. Research confirmed that **every prior phase excluded `PATIENT_SUBJECT` from all read access** (Study/Site/Subject/Visit/Task/Dashboard/Monitoring/Milestone all gate it out), and **no endpoint anywhere did row-level ("only my own record") filtering** — every phase to date scoped access purely by role. There was also **no Subject-to-User account linkage of any kind**, and **no user-creation API had ever been built** — all 10 prior phases' test users were raw SQL inserts. Phase 8's own report flagged this explicitly as an open item for this phase to resolve.

Four decisions were confirmed with you before implementation:

1. **Account creation — staff-triggered, deterministic default password, no email/SMS dependency.** Because this deployment runs on a corporate network where outbound email/SMS may be unreliable, a Site Coordinator/Study Manager/Admin triggers account creation directly on an enrolled Subject; the backend computes a deterministic temporary password from the subject's own name + DOB and forces a password change on first login, reusing the already-built `passwordExpiresAt`/`mustChangePassword` mechanism and `ChangePasswordComponent` — no new frontend plumbing was needed for the forced-change flow itself. The same mechanism backs "Reset Patient Password."
2. **Row-scoping — entirely new `/api/patient/*` endpoints**, not extensions of existing staff controllers. Every patient endpoint resolves "which Subject am I" server-side from the authenticated principal via `PatientContextService`, never from a client-supplied ID. No existing staff-facing endpoint or query was touched.
3. **Scope — the 5 committed stories plus AE self-reporting.**
4. **Document upload trust boundary — requires staff review before CURRENT.** Patient uploads start at `DocumentVersionStatus.DRAFT` and are immediately submitted into Phase 2's existing review workflow, landing directly in staff's existing approval queue — no new approval mechanism.

## 2. What Was Built

### Backend

- **`Subject.linkedUser`** (nullable, unique FK to `User`) — the entire account-linkage mechanism.
- **`SubjectPortalAccountService`** (`subject/service`): `createPortalAccount`/`resetPortalPassword`, both computing a deterministic username (`subjectCode.toLowerCase()`) and password (`{FirstName}@{DOBddMMyyyy}#1`, engineered to always satisfy the real password-complexity policy regardless of name length) from the Subject's *current* name/DOB, forcing `passwordExpiresAt` into the past so the existing forced-change flow fires. Both return the plaintext temporary password once, for staff to relay to the patient in person.
- **New `com.ctms.ctms_backend.patientportal` package**: `PatientContextService` (the single chokepoint resolving a patient's own Subject from their JWT identity), `PatientVisitController` (delegates straight to the existing `VisitService.schedule`), `PatientDocumentController` + `PatientDocumentUploadService` (uploads via a new `DocumentService.createPendingReviewDocument`, then `DocumentWorkflowService.submitForReview` — same review queue staff already use), `PatientProfileController` + a new `SubjectService.updateOwnProfile` (contact fields only), `PatientAdverseEventController` (thin wrapper over Phase 7's existing `AdverseEventService.report`).
- **Notification hooks**: `VisitSchedulingService` (new visit scheduled) and `VisitService` (rescheduled, completed) each gained a null-guarded call into the existing, unmodified `NotificationService` — fires only when a subject has a linked portal account.
- **RBAC**: every `/api/patient/*` endpoint is `PATIENT_SUBJECT`-only; the two new staff-facing account-management endpoints on `SubjectController` reuse its existing `WRITE_ROLES`.

### Frontend

- Role-aware fallback: `roleGuard` and `login.component.ts` now send a `PATIENT_SUBJECT`-only session to `/patient` instead of the staff `/dashboard`.
- `core/patient/*.service.ts` (new): thin services for visits, documents, profile, adverse-events — none of them ever send a subject ID from the client.
- `layout/patient-shell/`: a separate, patient-branded shell ("My Visits", "My Documents", "My Profile", "Report a Health Issue"), reusing the existing generic `/api/notifications` endpoints and `NotificationBellComponent` unchanged.
- `features/patient/*`: visit list, document list/upload, profile edit (contact fields only), AE self-report form.
- Staff-side: "Create Portal Account" / "Reset Patient Password" buttons on `subject-detail.component`, displaying the returned temporary password once in a dismissible panel.

## 3. Defects Found & Fixed

- **Cross-patient document leak (caught during manual curl verification, fixed before your walkthrough).** The initial `PatientDocumentController.list` scoped a patient's document list by **study**, reusing the same pattern as Phase 10's `DocumentRequirement` scoping. Since two patients are routinely enrolled in the same study, this let Patient B see Patient A's personal uploaded documents. Root cause: `Document` has no subject-level concept, only a study FK — patient documents needed to be scoped by **who uploaded them** (`Document.owner`, already correctly set to the uploading patient's own `User`), not by study. Fixed by adding `DocumentRepository.findByOwnerIdAndVisibleTo` and `DocumentService.listByOwner`, and added a dedicated integration-test assertion (`secondPatientNeverSeesFirstPatientsData`) plus a manual curl re-verification confirming the fix.
- **Password-reset history collision (caught by the integration test, fixed before it ever reached curl/browser testing).** Because the account-creation and reset flows recompute the *same* deterministic password from a subject's unchanging name/DOB, resetting a patient's password after they'd changed it once always recomputed a value already sitting in their password history — tripping the "must not reuse last 5 passwords" policy on every single reset. Fixed by adding a `checkHistory` parameter to `PasswordPolicyValidator.validate` (defaulting to `true`, preserving existing `AuthenticationService`/`AdminBootstrapRunner` behavior unchanged) and skipping the history check specifically for `resetPortalPassword`, since that policy exists to stop a person voluntarily cycling back to a password they personally chose — not applicable to a staff-triggered, system-recomputed recovery credential.
- **Angular Material label/placeholder overlap** on the "Document Type" upload field (found during your browser walkthrough) — combining a `<mat-label>` and a `placeholder` on the same input caused them to render overlapping until focus. Fixed by removing the placeholder and adding a `<mat-hint>` below the field instead, matching the pattern already used elsewhere in the app (e.g. the visit-template "Depends On" hint).

## 4. Verification

- **Unit tests**: `SubjectPortalAccountServiceTest` (4 cases, using the *real* `PasswordPolicyValidator` — not mocked — to prove the deterministic formula actually satisfies live policy), `PatientContextServiceTest` (2), `PatientDocumentUploadServiceTest` (1), `SubjectServiceTest` (extended — `updateOwnProfile` proven to leave staff-only fields untouched), `VisitSchedulingServiceTest`/`VisitServiceTest` (extended — notification hooks fire only when `linkedUser` is set).
- **Integration tests** (`PatientPortalIntegrationTest`, `@SpringBootTest @Transactional` against `ctms_testdb`, 3 cases): full lifecycle from account creation → forced password change → login → visit schedule → document upload (confirmed `PENDING_REVIEW`, not `CURRENT`) → profile update (confirmed staff-only fields untouched) → AE self-report; cross-patient isolation (document leak test, added after the bug was found); password-reset-after-change (added after the history-collision bug was found). Full backend suite: **197/197 passing** (up from 164 pre-Phase-11).
- **Manual `curl` pass** against real Postgres (dev DB, restarted twice to pick up fixes): full account-creation → forced-change → patient-endpoint happy path; document upload landing `PENDING_REVIEW` (confirmed directly in the DB); notification hooks firing on visit completion; RBAC blocking in both directions (patient blocked from every staff endpoint, staff blocked from every patient endpoint); the cross-patient document leak reproduced, then re-verified fixed.
- **Browser walkthrough** with you: account creation, forced password change landing on `/patient` (not the staff dashboard), visit schedule, document upload/pending-review, profile edit, AE self-report, notifications, RBAC redirects, and cross-patient isolation all confirmed working. One styling issue was found and fixed (label/placeholder overlap, §3).
- **Frontend**: `ng build --configuration production` and `ng test` both pass; all 4 new patient page chunks confirmed present in the build output.

## 5. Known Gaps / Carried-Forward Items

- **Test results remain out of scope** — never a named story for this phase, and the plan explicitly excluded them.
- **No scheduled "day-before" visit reminders** — notifications only fire on real events (scheduled/rescheduled/completed), since no cron/scheduler infrastructure exists yet in this codebase and it wasn't a named acceptance criterion.
- **Deterministic password formula recomputes from current name/DOB**, not a frozen value — if a patient's name is corrected by staff later, a subsequent reset will compute a different password than the original. This is expected/desirable behavior (the credential should reflect current data), just worth noting for anyone reading the reset endpoint's behavior cold.
- **A patient's document-list scope (`owner`) vs. Phase 3/10's other document/document-requirement mechanisms (`study`) are two genuinely different scoping shapes** in the same `document` package now — worth keeping in mind if a future phase adds another document-consuming surface, to pick the correct one deliberately rather than copying whichever is nearest.

## 6. Ready for Next Phase

The Patient Portal is fully functional and independently verified, reusing Phase 0's password-policy/forced-change infrastructure, Phase 2's document review workflow, Phase 5's visit scheduling, Phase 6's notification service, and Phase 7's adverse-event mechanism without duplicating any of them — while introducing the project's first genuinely ownership-scoped (not just role-scoped) RBAC surface, cleanly isolated in its own package so no existing staff-facing code was touched.
