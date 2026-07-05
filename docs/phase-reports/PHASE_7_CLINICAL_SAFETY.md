# Phase 7 — Clinical Safety: Test Results & Adverse Events: Completion Report

**Status:** Complete
**Scope:** Carried-over BL-06/07/08 (not part of the official v0.2 backlog Epics 1–9) — record/review test/lab results, attach source lab reports, adverse event reporting with severity-based escalation.

---

## 1. Context

This phase's requirements text (`CTMS_Requirements_Reference.md` Part D) is much thinner than the Epic-numbered stories — single user-story lines with a handful of acceptance criteria, no glossary entries, no RBAC matrix, and the document's own note says these stories "were not re-estimated for size/priority against the v0.2 backlog scale." Several mechanics were left entirely unspecified and were resolved via `AskUserQuestion` before implementation began:

1. **AE severity scale** — `MILD, MODERATE, SEVERE, LIFE_THREATENING` (a real clinical-trial taxonomy, not Task's generic `LOW/MEDIUM/HIGH`).
2. **Escalation threshold** — `SEVERE`/`LIFE_THREATENING` auto-create an escalation Task via Phase 6's engine; `MILD`/`MODERATE` do not.
3. **Test Result status model** — `RECORDED → REVIEWED` (linear) plus an **independent `abnormal` boolean**, deliberately not a 3-state `PENDING/REVIEWED/FLAGGED` enum, so an abnormal value is visible immediately at entry time and "reviewed" vs. "clinically concerning" are never conflated.
4. **AE reporting RBAC this phase** — `INVESTIGATOR` and `SITE_COORDINATOR` (participant-side reporting is explicitly Phase 11's Patient Portal).

A few additional lower-stakes design calls were made directly and documented in the plan rather than re-asking: the `AdverseEvent` `OPEN → UNDER_REVIEW → RESOLVED` sequence (already given by the implementation plan doc itself), `RESOLVED` as a dedicated action requiring mandatory `resolutionNotes` (mirrors Subject withdrawal/Study closeout's pattern), splitting Test Result RBAC so recording is broader than reviewing (review implies clinical judgment), and making `TestResult.visit` required but `AdverseEvent.visit` optional.

## 2. What Was Built

### Backend

- **Schema** (`V10__test_results_and_adverse_events.sql`): `test_result`, `test_result_attachment`, `adverse_event` tables, plus a new active `RuleDefinition` version (2) for the existing `TASK_RULES_DEFAULT` rule set adding a 4th DRL rule, `ADVERSE_EVENT_HIGH_SEVERITY` — a whole-DRL replacement (rule definitions aren't patches), mirroring how `RuleSetService.addDefinition` works at runtime.
- **`com.ctms.ctms_backend.testresult`**: `TestResult` (linked to `Subject` + `Visit`, `RECORDED`/`REVIEWED` status independent of an `abnormal` boolean), `TestResultAttachment` (mirrors `DocumentVersion`'s exact field shape), `TestResultService` (record/review/list/get), `TestResultAttachmentService` (reuses Phase 2's `StorageService.store`/`retrieve` and the same SHA-256 checksum pattern as `DocumentService`, unmodified), `TestResultController` + `TestResultAttachmentController`.
- **`com.ctms.ctms_backend.adverseevent`**: `AdverseEvent` (severity + guarded `OPEN→UNDER_REVIEW→RESOLVED` status), `AdverseEventService` (report/transition/resolve/list/board), `AdverseEventController`. `report()` resolves owner (site's `assignedCra` else subject's `createdBy`) and escalation target (`subject.getStudy().getCreatedBy()`) at the call site — identical to Phase 6's `VISIT_MISSED` pattern — and calls the unmodified `TaskService.createTask(...)` when severity is `SEVERE`/`LIFE_THREATENING`.
- **RBAC**: Test Result recording — `SITE_COORDINATOR, INVESTIGATOR, STUDY_MANAGER, ADMIN`; review — `INVESTIGATOR, STUDY_MANAGER, ADMIN` only. AE reporting — `SITE_COORDINATOR, INVESTIGATOR, ADMIN`; transition/resolve — `INVESTIGATOR, STUDY_MANAGER, ADMIN`; board — `INVESTIGATOR, STUDY_MANAGER, ADMIN, CRA_MONITOR`.
- **Audit**: every create/state-change/attachment-access wired through the shared `AuditService`, no per-feature logging.
- **5 new exceptions** wired into the existing single `GlobalExceptionHandler`.

### Frontend

- `core/test-results/test-result.service.ts`, `core/adverse-events/adverse-event.service.ts`.
- `features/subjects/subject-detail/`: two new cards — Test Results (record form, review button gated to review roles, per-result attachment upload/download) and Adverse Events (report form, inline Start Review/Resolve actions).
- `features/adverse-events/ae-board/`: new standalone Kanban page (OPEN/UNDER_REVIEW/RESOLVED columns, severity-colored cards, cross-subject), linked from the shell nav for board-scoped roles, routed at `/adverse-events/board`.

## 3. Defects Found & Fixed

1. **Reused exception with a misleading message.** Initially reused `StudySiteMismatchException` (from Phase 4, "Site X does not belong to study Y") for the case of a `Visit` not belonging to the `Subject` on a `CreateTestResultRequest`. Caught during implementation, before any test ran — fixed by adding a dedicated `VisitSubjectMismatchException` with an accurate message instead of stretching an unrelated exception's semantics.
2. **Download links bypassed the auth interceptor (found via your browser walkthrough).** Test Result attachment downloads and Document downloads both used a plain `<a [href]="...">` pointing at the API URL. Since browser-navigated anchor clicks never go through Angular's `HttpClient`, the `authInterceptor` never attached the JWT, and the backend correctly 403'd every download. Fixed by switching both to a blob-based download: `HttpClient.get(url, { responseType: 'blob' })` followed by a synthetic anchor click on an object URL. This was a **pre-existing latent bug in Document Management** (Phase 2) that had gone uncaught until Phase 7's attachment feature exercised the same pattern and you actually clicked a download link — fixed in both places per your request, since the root cause and fix were identical.

## 4. Verification

- **Unit tests**: `TestResultServiceTest` (5 cases — happy path, visit/subject mismatch, review guard, already-reviewed rejection), `TestResultAttachmentServiceTest` (2 cases — upload/download delegate correctly to `StorageService`), `AdverseEventServiceTest` (7 cases — MILD/SEVERE/LIFE_THREATENING escalation behavior with mocked `TaskService`, guarded transition sequence, resolve requiring notes).
- **Integration tests** (`ClinicalSafetyIntegrationTest`, `@SpringBootTest @Transactional` against the dedicated `ctms_testdb`, 4 cases): full record→review→attach→download round-trip with byte-for-byte content verification; `MILD` AE creates no task; `SEVERE` AE creates a real `ADVERSE_EVENT_HIGH_SEVERITY` task via the **real (unmocked) Drools rule set**, asserting correct owner/escalation-target resolution; full `OPEN→UNDER_REVIEW→RESOLVED` lifecycle with a rejected early-resolve attempt; audit trail present throughout. Full backend suite: **124/124 passing** (up from 107 pre-Phase-7).
- **Manual `curl` pass** against real Postgres: recording as Site Coordinator + 403 on Site-Coordinator-attempted review; attachment upload/download byte-for-byte match; MILD AE → no task, SEVERE AE → real escalation task visible in the owning Site Coordinator's task inbox with the correct pre-resolved escalation target; resolve-from-`OPEN` rejected (400), resolve-without-notes rejected (400), full lifecycle success; board 403 for Site Coordinator, 200 for Investigator; Patient blocked (403) from both Test Results and Adverse Events reads; full audit trail confirmed directly against `audit_log`.
- **Browser walkthrough** with you: recording, review-role gating, attachment upload, AE reporting/escalation/lifecycle, and the AE board all confirmed working on the first pass. The two download-link 403s above were caught this way — not by any automated test, since they were a browser-navigation-vs-`HttpClient` distinction invisible to `curl` (which always sends the header explicitly) and invisible to unit/integration tests (which call the service layer directly, never routing through the interceptor).
- **Frontend**: `ng build --configuration production` and `ng test` both pass after all fixes.

## 5. Known Gaps / Carried-Forward Items

- `subject-detail` is now carrying Visit Schedule, Test Results, and Adverse Events cards on top of the core subject fields — flagged as a candidate for a tabbed layout in a later phase, not addressed here.
- Per BRD §A.3.2 Out of Scope ("safety systems, or lab data management **beyond operational linkage**"), this phase deliberately stays at operational linkage — no clinical data analysis, no lab-value normalization/units validation, no MedDRA-style coding of AE terms.
- Participant-side AE self-reporting remains deferred to Phase 11's Patient Portal, per the implementation plan's own note.

## 6. Ready for Next Phase

Test Results and Adverse Events are fully functional, independently verified, and reuse Phase 2's storage layer and Phase 6's task/escalation engine without modification — consistent with this project's pattern of extending shared infrastructure rather than duplicating it per feature.
