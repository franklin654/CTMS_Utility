# Phase 4 — Subject Management: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 3 (Stories 01–04): enroll a subject with eligibility validation, track lifecycle status, update subject details, view subject profile.

---

## 1. What Was Built

### Backend (`com.ctms.ctms_backend.subject`)

- **Schema** (`V6__subject_management.sql`): `subject` table (study + site FKs, demographics, contact/emergency-contact, `notes`/`medical_history` as two distinct fields, locked `screening_date`), `eligibility_criterion` (per-study config: label + INCLUSION/EXCLUSION type), `subject_eligibility_answer` (append-only per-criterion answer log captured at enrollment), `subject_status_history` (append-only transition log). Also seeds a shared **`ELIGIBILITY_DEFAULT`** Drools rule set (Phase 0's `RuleSetService`/`DroolsRuleEngine`) with its default DRL.
- **Eligibility genuinely routes through the rules engine**, not a hardcoded gate: a per-study `EligibilityCriterion` config table drives *what* gets asked at enrollment (data, not code, per CLAUDE.md §2.7), while the single shared `ELIGIBILITY_DEFAULT` DRL evaluates the submitted answers (any unmet INCLUSION or any met EXCLUSION → violation) via `RuleSetService.evaluate("ELIGIBILITY_DEFAULT", facts)`. This was a deliberate confirmed design choice — the BRD itself flags eligibility mechanics as an open, under-specified gap.
- **State machine**: linear forward progression `SCREENED → ENROLLED → IN_TREATMENT → COMPLETED` via `SubjectLifecycleService.transition`, with `WITHDRAWN` reachable from any non-terminal state via a dedicated `withdraw` action requiring a mandatory reason code — mirrors Study's transition-vs-closeout split and Site's activation split.
- **Role-aware `SubjectResponse`**: the first phase with a *BRD-confirmed* field-hiding requirement (Story 04 AC2: "Role-based access controls hide sensitive fields (e.g., medical notes)"). `medicalHistory` is nulled out in the DTO factory for any caller whose roles don't include `STUDY_MANAGER`, `SITE_COORDINATOR`, `INVESTIGATOR`, or `ADMIN` — resolved from `SecurityContextHolder` using the same role-extraction helper pattern as Phase 2's `DocumentAccessControlService.currentRoleCodes()`.
- **Locked fields**: `subjectCode` and `screeningDate` are simply never accepted by `UpdateSubjectRequest` at all (no runtime lock-check needed, unlike Study's status-based lock — Subject has no "draft" pre-state).
- **RBAC**: write endpoints (enroll/update/transition/withdraw) → `SITE_COORDINATOR`, `STUDY_MANAGER`, `ADMIN` (Story 01–03's actor is Site Coordinator; the V1-seeded `STUDY_MANAGER` role description literally says "Study/site/subject lifecycle"); read → all internal roles except `PATIENT_SUBJECT`. Eligibility criteria: write (`create`/`deactivate`) gated `STUDY_MANAGER`/`ADMIN`, but **read is also open to `SITE_COORDINATOR`** — caught during design review, since Site Coordinators are the ones actually enrolling subjects and need to see the criteria checklist.
- **Audit + notifications**: every enrollment/update/transition/withdrawal writes to the shared `AuditLog`; lifecycle changes notify the subject's creator and the site's assigned CRA (reusing Phase 3's `Site.assignedCra`) if one exists.
- **6 new exceptions** wired into the existing single `GlobalExceptionHandler`, including a structured `EligibilityFailedException` handler (returns `violations` as a real JSON array, matching Phase 3's `SiteActivationBlockedException` pattern) and a dedicated `IncompleteEligibilityAnswersException`/`InvalidCraAssignmentException`-style exception (learned from a Phase 3 bug) instead of a raw `IllegalArgumentException` that would've fallen through to a generic 500.

### Frontend (`features/subjects/`, `features/eligibility-criteria/`)

- `subject-list` (paginated, study filter, status chip), `subject-enroll` (demographic form that **dynamically fetches the selected study's active eligibility criteria** and renders a checkbox per criterion — the config-driven eligibility UI — with a blocked-enrollment response's `violations` array rendered as a clear list, same pattern as Phase 3's `missingItems`), `subject-detail` (profile view, inline lifecycle-advance and withdraw-with-reason controls, edit form with locked fields simply omitted, status history table). `medicalHistory` only renders if present in the response — the backend already nulls it out per-role, so no client-side role-gating duplicate logic needed for that specific field.
- `features/eligibility-criteria/` — a standalone admin page (linked from `study-detail` via a new "Eligibility Criteria" button) for `STUDY_MANAGER`/`ADMIN` to define criteria per study before enrollment opens.
- Routes wired under `ShellComponent`, "Subjects" nav link added next to Sites/Documents.

## 2. Defects Found & Fixed (Design-Time, Not Runtime)

Unlike Phases 2–3, no runtime bugs were found during browser verification this phase — both defect classes below were caught and fixed **during implementation**, before verification, by applying lessons learned from prior phases:

1. **Eligibility-criteria read RBAC gap.** Initially gated the `GET /api/eligibility-criteria` endpoint to `STUDY_MANAGER`/`ADMIN` only (matching the write roles), which would have silently broken the enrollment UI for Site Coordinators — they need to read the criteria list to render the checklist but aren't allowed to define criteria. Split into separate `WRITE_ROLES`/`READ_ROLES` before this ever reached testing.
2. **Raw `IllegalArgumentException` for incomplete eligibility answers.** Following the exact bug class fixed in Phase 3 (CRA assignment), the initial `SubjectService.enrollSubject` implementation threw a plain `IllegalArgumentException` when the enrollment request didn't answer every active criterion — which isn't wired into `GlobalExceptionHandler` and would fall through to a generic 500. Replaced with a dedicated `IncompleteEligibilityAnswersException` (400) before compiling.

## 3. Verification

- **Unit tests** (20 cases, `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`): `SubjectServiceTest` (6 — study/site mismatch, incomplete answers, eligibility-failure blocks creation with a mocked `RuleSetService`, subject-code generation, `medicalHistory` hidden from `CRA_MONITOR` / visible to `STUDY_MANAGER`) and `SubjectLifecycleServiceTest` (8 — forward transitions, skip-rejection, withdrawal from each of the 3 allowed states, withdrawal from `COMPLETED`/already-`WITHDRAWN` rejected, target-`WITHDRAWN`-via-transition rejected).
- **Integration tests** (2 cases, `SubjectManagementIntegrationTest`, `@SpringBootTest @Transactional` against `ctms_testdb`) — critically, these run **real Drools evaluation**, not a mocked `RuleSetService`: seed a study with 2 real criteria, enroll with the inclusion criterion unmet → blocked with the exact expected violation message from the actual DRL firing; enroll with both satisfied → `SCREENED` with a generated `SUBJ-######` code; full lifecycle to `COMPLETED`; separate withdrawal-from-`ENROLLED` path; audit/notification row counts confirmed.
- **Manual `curl` sequence** against real Postgres with 5 real bcrypt-hashed users across different roles: criteria definition, Site-Coordinator read access to criteria, blocked enrollment with the correct `violations` array, successful enrollment with subject-code generation, full forward lifecycle, skip-transition rejection, withdrawal without/with reason code, `medicalHistory` hidden from `CRA_MONITOR` / visible to `STUDY_MANAGER`, `PATIENT_SUBJECT` read 403, `INVESTIGATOR` write 403, full audit trail confirmed via `GET /api/audit-logs`.
- **Browser walkthrough** with you: eligibility criteria definition from the study-detail page, dynamic per-study checklist during enrollment (blocked then corrected), lifecycle advancement, and withdrawal with a reason — all confirmed working on the first pass.

## 4. Known Gaps / Carried-Forward Items

- **Visit schedule/history and deviations** are not implemented — the BRD gives no CRUD story for deviations (same treatment as Adverse Events being deferred to Phase 7) and visit history is Phase 5 territory; the subject profile page simply omits those sections for now.
- **Eligibility mechanics were the BRD's own flagged open gap** — this phase's design (per-study config table + shared Drools rule set) is a confirmed, deliberate choice, not a literal BRD requirement; if a future epic calls for per-study custom evaluation logic beyond simple all-inclusion/no-exclusion, per-study rule sets can be added later without changing the `EligibilityCriterion` config-table shape.
- Same Docker/Testcontainers gap as prior phases — integration tests use the dedicated `ctms_testdb` Postgres database instead.
- No `SubjectFieldLockedException`/runtime lock-check exists — deliberately omitted since locked fields are simply absent from `UpdateSubjectRequest`'s field set, so there's nothing to defensively check at runtime.

## 5. Ready for Phase 5
