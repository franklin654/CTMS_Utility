# Phase 8 — Monitoring & Reporting: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 6 (Stories 01–04): CRA site assignment (extended with backup CRA), monitoring visit logging + report attachments, real-time study dashboards with PDF/Excel export, and FPI/LPI/LPO/DBL milestone tracking with deadline alerts.

---

## 1. Context

Epic 6's BRD text (`CTMS_Requirements_Reference.md` lines 815–869) is thin in the same way Phase 7's was: literal acceptance criteria with no RBAC matrix, no "high-risk site" definition, no monitoring-visit-type/checklist structure, no milestone-alert threshold. Four decisions were resolved with you directly before implementation:

1. **"High-risk site" rule** — a **fixed threshold in code** (missed-visit rate > 20% OR 2+ open `SEVERE`/`LIFE_THREATENING` adverse events), not a Drools rule set, since CLAUDE.md §2.1 bans scored/predictive risk models and this needed to stay simple and explainable. **Per your explicit instruction, this is flagged here for future revisit**: if per-study configurability of these thresholds becomes a real need, migrate them into a Drools rule set (like `TaskRuleService`'s SLA rules) rather than hardcoded constants — the constants are already named and centralized in `DashboardService` specifically to make that migration easy later.
2. **Dashboard scoping** — role-scoped: `CRA_MONITOR` sees only their assigned/backup sites; everyone else sees portfolio-wide data. This had to be narrowed during implementation once the data was inspected: `Site.principalInvestigatorName`/`contactName` are free-text strings, not `User` FKs, so there is **no data-backed way** to scope an Investigator's or Site Coordinator's dashboard to "their" sites — only `CRA_MONITOR` has a real `User` relationship to `Site` (`assignedCra`/`backupCra`). Investigator/Site Coordinator dashboards are portfolio-wide this phase, same as Study Manager/Executive/etc. — a documented gap, not a silent drop.
3. **Milestone alerts** — notification-only, 7-day lookahead, mirroring `VisitAlertService` exactly (no Task creation).
4. **CRA assignment retrofit** — BRD Story 01 AC#4 ("notifications sent to assigned CRA(s)") was missing from Phase 3's `SiteService.assignCra()`. Closed as part of adding backup-CRA support.

## 2. What Was Built

### Backend

- **Schema** (`V11__monitoring_and_milestones.sql`): `site.backup_cra_id` column; new `monitoring_visit`, `monitoring_visit_report`, `milestone` tables (unique `(study_id, milestone_type)` constraint on the latter).
- **`SiteService.assignCra`** extended to accept an optional `backupCraUsername`, validates both against `Role.CRA_MONITOR`, audits both assignments separately, and now sends a `CRA_ASSIGNED` notification to the primary (and backup, if set) — closing the Story 01 gap. Backup CRA has symmetric operational permissions with primary (either can log monitoring visits).
- **`com.ctms.ctms_backend.monitoring`**: `MonitoringVisit` (site + CRA FKs, `visitType` enum `SIV/IMV/COV`, a log entry not a lifecycle — no status field, freely editable with standard audit logging, no mandatory reason code), `MonitoringVisitReport` (mirrors `TestResultAttachment`'s field shape exactly, reuses Phase 2's `StorageService` unmodified).
- **`com.ctms.ctms_backend.milestone`**: `Milestone` (study FK, `milestoneType` enum `FPI/LPI/LPO/DBL`, `plannedDate`/`actualDate`), `MilestoneService` (create with proactive duplicate-type rejection, planned-date edits locked once `actualDate` is set, `recordActual` as a dedicated action mirroring Subject withdrawal/AE resolve's pattern), `MilestoneAlertService` (mirrors `VisitAlertService` field-for-field: daily 6am sweep, 7-day lookahead, dedup via `NotificationService.alreadyNotified`).
- **`com.ctms.ctms_backend.dashboard`**: `DashboardService` (KPI aggregation — enrollment/site-activation/visit-adherence/sites-by-country/high-risk-sites/milestones — role-scoped per the decision above), `DashboardExportService` (PDF via `openpdf`, Excel via `poi-ooxml` — both dependencies were already in `pom.xml`, unused until now), plus a `GET /api/dashboard/filter-options` endpoint (added after your browser-testing feedback — see §3) returning the distinct countries/phases already in use, since neither field has a fixed vocabulary anywhere in the system.
- **RBAC**: monitoring-visit write — `CRA_MONITOR, ADMIN`; milestone write — `STUDY_MANAGER, ADMIN`; dashboard/monitoring-visit/milestone read — broad (all roles except `PATIENT_SUBJECT`); AE-board-style oversight not needed here since the dashboard itself is the oversight view.
- **7 new exceptions** wired into the existing single `GlobalExceptionHandler`.

### Frontend

- `core/monitoring-visits/monitoring-visit.service.ts`, `core/milestones/milestone.service.ts`, `core/dashboard/dashboard.service.ts` (new).
- `features/sites/site-detail/`: added a Backup CRA autocomplete field alongside the existing CRA assignment UI, plus a new Monitoring Visits card (log form, report upload/download via blob-download — the Phase 7 auth-interceptor lesson applied from the start this time).
- `features/milestones/`: new routed page at `/studies/:studyId/milestones` (planned-vs-actual table, delayed rows highlighted red, dedicated "Record Actual" action), linked from `study-detail`.
- `features/dashboard/dashboard.component.ts`: **replaced the Phase 0 placeholder** — filter controls (Study ID/Country/Site ID/Phase, the latter two now autocomplete-backed per your feedback), `ng2-charts` bar/pie charts for enrollment and site activation, visit adherence rate, sites-by-country, high-risk sites list, milestone summary table, PDF/Excel export buttons (blob-download pattern).

## 3. Defects Found & Fixed (via your browser walkthrough)

1. **Country/Phase filters were plain text inputs with no suggestions.** You pointed out this was poor UX for fields you'd want to search/select from. Neither `Site.country` nor `Study.phase` has a fixed vocabulary anywhere in the BRD or codebase (confirmed by checking both entities and every existing form — both are free text). Rather than hardcoding an arbitrary list, added `GET /api/dashboard/filter-options` returning the **distinct values already in use** in the database, and wired both fields to `MatAutocomplete` client-side, filtering the fetched list as the user types.
2. **Patient users hit a raw red "access denied" error on the dashboard**, which is confusing UX distinct from a real bug — the RBAC itself was correct (Patient is intentionally excluded from Epic 6's staff-facing dashboard), but the failure mode was jarring. Replaced the generic error banner with a specific 403 handler that shows a plain-language explanation instead. **This is a stopgap, not the real fix**: there is no Patient-specific experience anywhere in the app yet (dashboard, tasks, AE board, monitoring visits, milestones — all assume staff roles), because the Patient Portal is explicitly **Phase 11** in the implementation plan. The correct long-term fix is a dedicated patient landing route/guard built in that phase, not a friendlier error message bolted onto staff pages. **Flagging this explicitly as a known gap to carry into Phase 11's planning**, per your request.

## 4. Verification

- **Unit tests**: `SiteServiceTest` (extended — backup CRA assignment + dual notification), `MonitoringVisitServiceTest`, `MonitoringVisitReportServiceTest` (mirrors `TestResultAttachmentServiceTest`), `MilestoneServiceTest` (duplicate-type rejection, planned-date-locked-after-actual, double-record-actual rejection), `MilestoneAlertServiceTest` (dedup + 7-day window), `DashboardServiceTest` (7 cases — CRA scoping, both high-risk trigger conditions, no-risk case, filter-options).
- **Integration tests** (`MonitoringAndReportingIntegrationTest`, `@SpringBootTest @Transactional` against the dedicated `ctms_testdb`, 4 cases): backup-CRA assignment with real notification rows for both CRAs; full monitoring-visit log + report upload/download round-trip with byte-for-byte verification; milestone duplicate-type rejection + delayed-flag correctness; CRA-scoped vs. portfolio-wide dashboard results genuinely differing across two sites/two CRAs. Full backend suite: **146/146 passing** (up from 124 pre-Phase-8).
- **Manual `curl` pass** against real Postgres: backup CRA assignment + notifications to both; Site Coordinator blocked from logging monitoring visits (403), backup CRA logging a visit successfully (symmetric permissions confirmed); report upload/download byte-for-byte match; milestone duplicate rejected (409), Site-Coordinator blocked from `record-actual` (403), double-record-actual rejected (400), planned-date edit after actual recorded rejected (400), delayed flag correct; dashboard scoping, Patient blocked (403), both PDF and Excel exports producing valid files (confirmed via `file`).
- **Browser walkthrough** with you: CRA assignment, monitoring visits, milestones, and the dashboard (including exports) all worked on the first pass. Two real UX issues were caught this way (country/phase autocomplete, the Patient 403 experience) — both fixed and reverified via a fresh backend restart + endpoint check.
- **Frontend**: `ng build --configuration production` and `ng test` both pass after all fixes.

## 5. Known Gaps / Carried-Forward Items

- **High-risk site thresholds are fixed constants**, not per-study configurable — explicitly flagged for a future Drools-based migration if that configurability is ever needed (see §1, decision 1).
- **Investigator/Site Coordinator dashboards are portfolio-wide, not site-scoped** — no `User` FK exists from `Site` to either role today; a real fix would mean adding that relationship, which Epic 6's BRD doesn't ask for and wasn't in scope here.
- **No dedicated Patient experience anywhere in the app** — the dashboard's 403 handling is a stopgap; the real fix is Phase 11's Patient Portal, which needs its own landing route/guard rather than routing patients into staff-facing pages at all. **This should be an explicit input into Phase 11's planning.**

## 6. Ready for Next Phase

Monitoring & Reporting is fully functional and independently verified, reusing Phase 2's `StorageService`, Phase 3's CRA-assignment infrastructure, and Phase 6's notification/alert pattern without modification to the reused pieces themselves — consistent with this project's pattern of extending shared infrastructure rather than duplicating it per feature.
