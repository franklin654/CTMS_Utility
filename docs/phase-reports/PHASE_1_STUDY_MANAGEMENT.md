# Phase 1 — Study Management: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 1 (Stories 01–04): create study, update study details, manage lifecycle states, view study details.

---

## 1. Governance Change Mid-Phase

Partway through planning, a `CLAUDE.md` governance file appeared in the repo (created ~2 minutes after the Phase 0 commit — confirmed via file mtime — so Phase 0 predates it and isn't "non-compliant," it simply didn't exist yet). Per your direction, **Phase 1 onward follows `CLAUDE.md` conventions exactly; Phase 0 was left as-is**. This means Phase 1 looks structurally different from Phase 0 in several deliberate ways:

| Convention | Phase 0 | Phase 1 (per CLAUDE.md) |
|---|---|---|
| Package layout | Flat (`document/`, `rules/`) | Sub-packages (`study/entity`, `.repository`, `.service`, `.dto`, `.controller`, `.exception`) |
| Audit columns | `createdAt`/`updatedAt` only | `createdBy` + `modifiedBy` (User FKs) added |
| Lifecycle status | `public static final String` constants | Java enum (`StudyStatus`), `@Enumerated(EnumType.STRING)` |
| Compliance-gated transitions | Not yet applicable | `CONDUCT → CLOSEOUT` requires e-signature (password re-auth + reason), reusing Phase 0's `ESignatureService`, per CLAUDE.md §2.5's "closeout-type transitions" rule |

## 2. What Was Built

### Backend (`com.ctms.ctms_backend.study`)
- **Schema** (`V3__study_management.sql`): `study` table (with `study_code`, lifecycle `status`, `createdBy`/`modifiedBy`) and `study_status_history` (queryable transition log, with an optional FK to `e_signature` for the closeout transition).
- **State machine**: strictly sequential `DRAFT → ACTIVE → CONDUCT → CLOSEOUT`, no skipping, no going backward, enforced in `StudyService` via a `Map<StudyStatus, StudyStatus>`.
- **Two transition endpoints**, split per the e-signature requirement:
  - `POST /api/studies/{id}/transition` — justification-text-only, for `DRAFT→ACTIVE` and `ACTIVE→CONDUCT`.
  - `POST /api/studies/{id}/closeout` — password + reason, calls `ESignatureService.sign(...)` for `CONDUCT→CLOSEOUT`. Wrong password fails cleanly (401) without touching the study's status.
- **Field locking**: `protocolId` locked once a study leaves `DRAFT`; the entire record is locked from any edits once `CLOSEOUT` (a "signed" record, per CLAUDE.md §2.5).
- **Auto-generated `studyCode`** (`ST-%06d`, derived from the entity's own DB id), using the same two-step-save pattern as `DocumentService.currentVersion`.
- **RBAC**: write endpoints restricted to `STUDY_MANAGER`/`ADMIN`; read endpoints open to nearly every internal role, explicitly excluding `PATIENT_SUBJECT` (no backlog signal patients need this).
- **Audit + notifications**: every create/update/transition writes to the shared `AuditLog`; every lifecycle transition notifies the study's creator via the Phase 0 notification framework.
- **5 new exceptions** wired into the existing single `GlobalExceptionHandler` (not a new per-feature handler), matching the established convention.

### Frontend (`features/studies/`)
- `study-list` (search + pagination), `study-create` (form), `study-detail` (overview, status-chip row, inline edit with field locking, transition history).
- Two distinct `MatDialog` flows: a plain justification dialog for the first two transitions, and a visually distinct (red/warn) password+reason dialog specifically for closeout.
- `StudyService` HTTP client, wired into `app.routes.ts` and the shell nav, following Phase 0's established patterns exactly (lazy-loaded standalone components, `FormGroup`/`FormControl` built directly rather than via `FormBuilder`, `*appHasRole` for UX gating backed by server-side `@PreAuthorize`).

## 3. Defects Found & Fixed

1. **`study_code` migration/entity mismatch.** The column was `NOT NULL`, but the code-generation pattern (matching `Document.currentVersion`) requires inserting the row *before* the code is computed from its own auto-generated id. Fixed by making the column nullable (mirroring `document.current_version_id`) — caught immediately via the first real `curl` test against Postgres, before this migration was committed.
2. **Auth interceptor false-logout bug.** The frontend's `authInterceptor` chained its "refresh failed, clear session" error handler *after* the retried request too, not just after the token-refresh call. This meant any legitimate 401 from a business endpoint (like a wrong password on the closeout e-signature dialog) got misinterpreted as "the session is dead," clearing the user's tokens and redirecting to `/login` — even though they were still validly logged in. Found via your manual browser testing, fixed by scoping the catch to only the refresh call.
3. **Misleading error message.** `ESignatureService` reuses the login flow's `InvalidCredentialsException` ("Invalid username or password") for wrong-password re-authentication, which reads oddly when the user is already logged in and just re-entering their password. Fixed on the frontend by overriding the displayed message specifically for the closeout dialog's 401 case, without touching the shared backend exception (which is still correctly worded for actual login failures).
4. **Stale dev server.** A `ng serve` process from Phase 0's browser testing had been running continuously (via its file watcher) the whole time, which is why an attempted fresh restart silently failed ("port already in use") without me noticing — it was still serving live-recompiled code the whole session, so nothing was actually stale in terms of logic, but it's worth remembering to explicitly track and stop background dev servers between work sessions.

## 4. Verification

- **Unit tests** (`StudyServiceTest`, 12 cases via `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`): cover every state-machine branch (valid transitions, skip/backward rejection, `CLOSEOUT` rejected via the wrong endpoint), both field-lock rules, duplicate protocol ID, and both closeout outcomes (wrong password / success).
- **Integration tests** (`StudyManagementIntegrationTest`, 2 cases): run against a **dedicated `ctms_testdb` database** (not the dev DB, and not Testcontainers — Docker still isn't available in this environment) — full lifecycle create→active→conduct→closeout, asserting real `AuditLog`, `Notification`, and `ESignature` rows get written, plus the field-lock rule.
- **Full manual `curl` sequence** against real Postgres: happy-path create, duplicate protocol ID (409), missing fields (400), update-while-draft, field-lock-after-draft (400), skip-transition rejection, wrong-password closeout (401, status unchanged), correct-password closeout, post-closeout full lock (400), transition history, RBAC checks for `CRA_MONITOR` (read-yes/write-no) and `PATIENT_SUBJECT` (read-no) — all via a temporary test user, cleaned up afterward.
- **Browser walkthrough** with you: create → detail → both transition dialogs → field-locking visuals → closeout with wrong then correct password → post-closeout lock. Two real bugs (items 2–3 above) were only caught this way, not by the automated tests, since they were frontend-only logic.

## 5. Known Gaps / Carried-Forward Items

- Same Docker/Testcontainers gap as Phase 0 — integration tests use a real (separate) Postgres database instead.
- No field-level role hiding on `StudyResponse` — per your confirmation, nothing in the current field set is obviously role-sensitive; revisit if the backlog identifies a specific sensitive field later.
- `phase` (e.g. "PHASE_III") is a validated free-text field, not tied to the rules engine — it's a fixed clinical vocabulary, not a per-study configurable business rule, so this doesn't conflict with CLAUDE.md §2.7.

## 6. Ready for Phase 2

Study Management is fully functional and independently verified. Phase 2 (Document Management & Compliance) can now build on top of it — including, per the Implementation Plan, eventually linking documents to arbitrary business entities like `Study`, which `study_status_history` was already designed to accommodate (the `esignature_id` FK pattern generalizes the same way).
