# CTMS Browser Test Guide

A comprehensive manual test checklist covering every feature and workflow across all shipped phases (0–13). Use this for exploratory UAT-style testing in the browser, independent of the automated test suite.

## Setup

Run `docs/demo/demo_seed.sql` against your target database first (or confirm it's already been run) — this guide assumes that seed data exists. All accounts use password **`Demo2026!Pass`**:

| Username | Role |
|---|---|
| `demo.admin` | Admin |
| `demo.mgr` | Study Manager |
| `demo.coord` | Site Coordinator |
| `demo.investigator` | Investigator |
| `demo.finance` | Finance Manager |
| `demo.auditor` | QA/Compliance Auditor |
| `demo.cra` | CRA/Monitor |

Confirm both backend (`:8080`) and frontend (`:4200`) are up before starting.

---

## 1. Auth & Account Management

- [ ] Log in as `demo.mgr` — lands on `/dashboard`.
- [ ] Log out, try 5 wrong passwords in a row for `demo.coord` — 6th attempt (even correct) should reject with "account locked" for the configured lockout window.
- [ ] `/forgot-password` with `demo.finance`'s email — should succeed silently (no user-enumeration leak on unknown emails — try a fake email too, same generic response).
- [ ] Log in, then use `/change-password` — wrong current password rejected; correct one succeeds and forces re-login.
- [ ] Confirm JWT refresh works transparently (stay logged in past the access-token expiry without being kicked out).

## 2. RBAC Spot Checks

- [ ] As `demo.coord` (Site Coordinator), try navigating to `/admin/rule-sets` and `/studies/new` directly by URL — should be blocked (role guard).
- [ ] As `demo.finance`, confirm `/payments` and a study's `/budget` page are visible; confirm `/admin/rule-sets` is not.
- [ ] As `demo.auditor`, confirm `/admin/audit-log` is visible and `/studies/new` is not.

## 3. Study Management

- [ ] As `demo.mgr`, `/studies/new` — create a new study. Confirm it lands in the list as `DRAFT`.
- [ ] Open it, transition `DRAFT → ACTIVE` (justification required), then `ACTIVE → CONDUCT`.
- [ ] Try to skip straight to `CLOSEOUT` from `CONDUCT` via the regular transition action — should require the dedicated closeout flow instead (reason + read-only lock afterward).
- [ ] Check the study's Status History table shows every transition with actor + timestamp.
- [ ] Confirm **Oncology Combination Therapy Trial** (seeded, already `CONDUCT`) shows correctly in the study list.

## 4. Site Management

- [ ] As `demo.mgr`, register a new site under any study.
- [ ] Complete its 5 activation checklist items one at a time — confirm it **auto-activates silently** on the last item (no e-signature prompt) — this is a known, documented gap, not a bug you need to report.
- [ ] Open **Metro General Hospital** (seeded, `ACTIVE`) — confirm checklist shows all 5 complete and an activation e-signature is recorded.
- [ ] Open **Riverside Regional Medical Center** (seeded, checklist complete but still `PENDING_ACTIVATION`) — click **Attempt Activation**. Confirm the e-signature dialog appears (password + reason), wrong password rejected, correct one activates the site.
- [ ] Assign a CRA to a site via the CRA autocomplete field — confirm it saves.
- [ ] On any site, add a Monitoring Visit (SIV/IMV/COV type, findings, issues) and confirm it lists.

## 5. Document Management

- [ ] `/documents/new` — upload a study-linked document (any category).
- [ ] Upload a second version on the same document — confirm it goes to `DRAFT`/`PENDING_REVIEW`, not straight to `CURRENT` (only the first version does that).
- [ ] Submit that version for review → as `demo.mgr` or `demo.auditor`, approve it via `/documents/approval-queue` — confirm e-signature required, and the previous version flips to `ARCHIVED`.
- [ ] Try rejecting a version with no comment — should be blocked (comment required on rejection).
- [ ] As `demo.cra` (CRA/Monitor), try opening a `FINANCIAL`-category document — should be denied (category-level access rule), even though CRA has general document read access.
- [ ] On a subject's page (e.g. James Whitfield), use the **Documents** card to upload a subject-linked document and confirm it lists there (not in the general study document list).

## 6. Subject Management

- [ ] As `demo.coord`, `/subjects/new` — enroll a subject. If the study has eligibility criteria configured, confirm all must be answered before submission.
- [ ] Transition a freshly-enrolled subject `SCREENED → ENROLLED → IN_TREATMENT` one step at a time.
- [ ] On **Aisha Bello** (seeded, `ENROLLED`, not yet withdrawn): click **Withdraw Subject** — confirm the form now asks for **both** reason and password. Try the wrong password (rejected, status stays `ENROLLED`), then the correct one (status flips to `WITHDRAWN`).

## 7. Visit Management & Consent Gate

- [ ] Under any study, `/studies/:id/visit-templates` — create a template, then a second one that **depends on** the first.
- [ ] Enroll a subject and confirm the visit schedule auto-generates with correct dates (screening date + target day).
- [ ] Try completing the **dependent** visit before its prerequisite — should be blocked (dependency guard).
- [ ] On **Robert Kim** (seeded, `SCREENED`, no consent document): try marking his Screening Visit **Complete** — should be blocked with a missing-consent error. Upload an `Informed Consent` document for him (via his Documents card), then retry — should now succeed.
- [ ] Mark a different visit **Missed** with a reason — confirm compliance rate on the schedule updates.
- [ ] Reschedule a visit — confirm it links back to the original (`rescheduledFromVisitId`) and the original shows as superseded.
- [ ] Schedule an ad-hoc visit (no template) — confirm it's excluded from the compliance-rate calculation.
- [ ] Edit a visit template's `targetDay` — confirm every still-`SCHEDULED` visit under it gets its date recomputed (already-completed visits untouched).

## 8. Task Automation & Escalation

- [ ] `/tasks` — as any staff role, confirm your task inbox shows only tasks owned by you (or `/tasks` broader view for managers).
- [ ] Confirm enrolling a subject, activating a site with no CRA, or missing a visit each auto-creates a task (check the inbox after each action above).
- [ ] Start and complete a task from the inbox.
- [ ] (Optional, needs waiting or backend time manipulation) Confirm an overdue, non-escalated task eventually flips to escalated.

## 9. Clinical Safety — Test Results & Adverse Events

- [ ] On a subject with a completed visit, record a Test Result linked to that visit — confirm it starts `RECORDED`, then review it (as Investigator) to flip to `REVIEWED`.
- [ ] Upload an attachment to a test result and download it back — confirm byte-for-byte match isn't corrupted.
- [ ] Report a **MILD** adverse event — confirm **no** task auto-creates.
- [ ] Report a **SEVERE** adverse event on the same subject — confirm an escalation task **does** auto-create, owned by the reporter, escalation target the study manager.
- [ ] Try resolving an AE that's still `OPEN` (not yet `UNDER_REVIEW`) — should be rejected as an invalid transition.
- [ ] Transition to `UNDER_REVIEW`, then resolve: wrong password rejected, correct password + notes succeeds, status flips to `RESOLVED`.
- [ ] `/adverse-events/board` — confirm the board view lists AEs across subjects for oversight roles.

## 10. Protocol Deviations

- [ ] On any subject, report a Protocol Deviation (description, severity, date) — confirm it lists immediately with **no** workflow/status (log-only, unlike AEs).
- [ ] Confirm **James Whitfield** (seeded) already shows his historical deviation.

## 11. Monitoring & Milestones

- [ ] Under a study, `/studies/:id/milestones` — create a milestone (target date), then record its actual completion date. Try creating a duplicate milestone type for the same study — should be rejected.
- [ ] Confirm a milestone significantly past its target date shows a "delayed" flag.

## 12. Dashboard

- [ ] `/dashboard` — as `demo.cra`, confirm the view is scoped to sites/studies you're assigned to (if applicable) vs. a portfolio-wide view for `demo.mgr`/`demo.admin`.
- [ ] Try the filter options and CSV export button.

## 13. Financial Management

- [ ] As `demo.finance`, under a study, `/budget` — create Budget v1 with a few line items. Create v2 with a reason — confirm v1 flips to `SUPERSEDED` and stays read-only/queryable.
- [ ] `/payments` — confirm a payment auto-generated from Maria Alvarez's completed visit shows `RELEASED` (seeded historical example) and another shows `PENDING`.
- [ ] Hold the `PENDING` payment with a reason. Release it: wrong password rejected, correct password + reason succeeds, status flips to `RELEASED`.

## 14. System Configuration

- [ ] `/admin/rule-sets` (Admin only) — open a rule set, edit its DRL, save as a new version. Try saving something syntactically invalid — should be rejected before activating.
- [ ] `/studies/:id/eligibility-criteria` — add a criterion, confirm it's required on subsequent enrollments for that study.
- [ ] `/studies/:id/document-requirements` — add a requirement, confirm it blocks the study's next lifecycle transition until satisfied.

## 15. Patient Portal

- [ ] On any enrolled subject's page, click **Create Portal Account** — note the generated username/temporary password shown once.
- [ ] Log out, log in as that patient account — confirm forced password change on first login, then lands on `/patient/visits` (not the staff dashboard).
- [ ] As the patient: view visit schedule, upload a document (confirm it lands `PENDING_REVIEW`, not `CURRENT`), edit profile (contact fields only — confirm clinical fields aren't editable), self-report an adverse event.
- [ ] Log back in as staff and confirm the patient's self-reported AE and uploaded document both show up on the staff side.
- [ ] Enroll a second patient in the same study and confirm they can't see the first patient's uploaded documents.

## 16. Audit Log & Traceability

- [ ] `/admin/audit-log` (Admin/Auditor only) — filter by entity name/ID, confirm CSV export includes before/after value columns.
- [ ] Pick an entity you just acted on above (e.g. the AE you resolved, or the payment you released) — enter its name/ID and click **View Traceability**. Confirm it shows both the full audit trail and the e-signature(s) tied to it.
- [ ] As `demo.investigator` (non-auditor role), try navigating to `/admin/audit-log` directly — should be blocked.

## 17. Account Settings (Self-Service Username/Email/Password)

New self-service page at `/account-settings` (staff shell — sidenav link) and `/patient/account-settings` (patient portal — top nav link), available to **every** role. Distinct from the existing `/change-password` forced-expiry page and from a patient's "My Profile" page (which edits `Subject.contactEmail`, a separate clinical contact field, not the login email tested here).

**⚠️ Renaming a `demo.*` account will break later steps in this guide that log in by that username.** Do the destructive checks below on the **disposable patient portal account** you created in §15 (safe to rename/break — it's not referenced by username anywhere else in this guide), and only test the staff-shell page's *appearance* and *email change* (non-destructive) against a `demo.*` account, changing the email back to its original value afterward if you want the guide to stay re-runnable.

- [ ] As any `demo.*` user, open `/account-settings` from the sidenav — confirm all three sections render (Change Username, Change Email, Change Password), matching the app's card/form styling elsewhere.
- [ ] **Wrong current password** on the username form — rejected, no change made.
- [ ] **Email change**, correct current password — succeeds with an inline "Email updated." confirmation, **you stay logged in** (no forced logout — email isn't tied to your session). Change it back to the original afterward.
- [ ] **Duplicate email** — try changing to another `demo.*` account's email — rejected (409, "already exists").
- [ ] Log in as the **patient account created in §15**, go to `/patient/account-settings` (top nav) — confirm the same three sections render in the patient-portal styling.
- [ ] **Username change**, correct current password — succeeds, and you're **immediately signed out** and redirected to `/login` (username is embedded in your session token, so changing it invalidates every active session, on every device).
- [ ] Log back in with the **new** username — confirm it works, and the old username no longer does.
- [ ] Confirm the patient's **"My Profile" contact email is unchanged** — the account-settings email change only touches the login credential, not `Subject.contactEmail`.
- [ ] `/admin/audit-log` — filter by entity `User` and the account's ID, confirm both the username and email changes appear with correct before/after values, and **no password ever appears in the log**.
