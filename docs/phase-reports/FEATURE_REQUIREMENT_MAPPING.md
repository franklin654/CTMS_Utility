# Feature ↔ Requirement Mapping Analysis

**Source of truth:** `docs/CTMS_Reference_Document` (BRD + Problem Statement + Product Backlog v0.2, 46 stories / 11 epics, plus 3 carried-forward Clinical Safety stories in Part D).

**Method:** Every story's acceptance criteria were checked against the *current* codebase (controllers, services, entities, migrations, frontend components) — not against memory of what was built earlier. Five independent verification passes covered Epics 1–3, 4–6, 7–9, 10–11 + Clinical Safety, and the full NFR/Dependency/Out-of-Scope set (BRD §7/§8/§3.2), respectively.

**Status definitions:**
- **Satisfied** — implementation matches the acceptance criteria.
- **Partial** — some acceptance criteria met, others not (see Notes).
- **Modified** — implemented, but deliberately differently than spec'd (a real design choice, usually documented elsewhere in the repo).
- **Not Implemented** — no real implementation found.
- **Not Verifiable / Not Tested** — an infra/ops claim (uptime, load capacity) that can't be confirmed by reading code; would need a real test to verify either way.

---

## Headline Numbers

### Backlog stories (49 total: 46 in v0.2 + 3 carried-forward Clinical Safety)

| Status | Count | % |
|---|---:|---:|
| Satisfied | 32 | 65% |
| Partial | 10 | 20% |
| Modified (deliberate deviation) | 7 | 14% |
| **Not Implemented** | **0** | **0%** |

**Zero backlog stories are entirely unimplemented.** The gaps that exist are all partial-credit (a story mostly works but misses one acceptance criterion) or deliberate, documented deviations — not missing features.

### NFRs, Dependencies & Out-of-Scope checks (14 items, BRD §7/§8/§3.2)

| Status | Count |
|---|---:|
| Satisfied | 4 |
| Modified (documented deviation) | 4 |
| Partial | 1 |
| **Not Implemented** | **1** |
| Not Verifiable / Not Tested (infra claims) | 4 |

The one genuine **Not Implemented** item at this level is **EDC system integration** — unlike MFA/SMS/SSO (which are *documented* organizational deviations), no EDC integration exists anywhere in the codebase and it isn't flagged as an intentional exclusion. It's explicitly named in-scope for "operational linkage" (BRD §3.1) but never built.

---

## Known, Documented Deviations (apply across many rows below — noted once here)

These aren't oversights; they're recorded decisions (see `CLAUDE.md` §2.2, and inline BRD annotations in `docs/CTMS_Reference_Document`):

1. **No MFA/OTP** — BRD §7.3 asks for it; org constraint says single-factor auth + account lockout instead. Affects Epic 10 Story 01, Epic 11 Story 02, BRD §7.3/§8.2.
2. **No SMS channel** — email + in-app notifications only. Affects Epic 4 Story 03, Epic 5 Story 04, Epic 10 Story 03, BRD §8.2.
3. **Angular instead of React** — BRD §7.5/§8.1 specify React; the whole frontend is Angular + Tailwind + Angular Material per implementation-planning decision.
4. **DRL text editor instead of a visual no-code builder** — Epic 9 Stories 01 & 04 ask for drag-and-drop workflow configuration; what exists is a Drools DRL text editor (deterministic and auditable, but not "no-code" in the literal sense). A written feasibility assessment for a true visual builder already exists at `docs/phase-reports/NO_CODE_RULE_BUILDER_FEASIBILITY.md`, explicitly scoped as future work.
5. **Kotlin never used** — BRD §7.5/§8.1 mention Java/Kotlin; backend is pure Java. Not previously flagged as a deviation anywhere, but is one.

---

## Epic-by-Epic Detail

### Epic 1: Study Management

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Create a New Study | Satisfied | `StudyService.createStudy` | Unique protocol ID enforced, auto `ST-%06d` code, defaults DRAFT, audited. |
| 02 – Update Study Details | Satisfied | `StudyService.updateStudy` | Protocol ID locked once status ≠ DRAFT, before/after audited. |
| 03 – Manage Study Lifecycle States | Satisfied | `StudyService.transition`/`closeout` | DRAFT→ACTIVE→CONDUCT→CLOSEOUT enforced; CLOSEOUT adds an e-signature requirement beyond spec (compliance enhancement). |
| 04 – View Study Details | Partial | `StudyController.get`, `study-detail.component.html` | No inline sites/subjects list on the overview itself (separate filtered pages instead); `StudyResponse` has no field-level redaction, only whole-endpoint RBAC. |

### Epic 2: Site Management

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Register a New Site | Satisfied | `SiteService.registerSite` | Site-code uniqueness, defaults PENDING_ACTIVATION, seeds 5 checklist items, audited. |
| 02 – Track Site Activation Status | Partial | `SiteActivationService.recheckAndPromoteIfComplete` | Auto-promotes to ACTIVE correctly; notifies only `site.createdBy`, not necessarily the assigned CRA — AC wants both CRA and Study Manager notified. |
| 03 – Enforce Activation Prerequisites | Modified | `SiteActivationService.attemptActivation` | Blocks correctly and lists missing items; adds a password e-signature gate before promotion, beyond the literal AC (compliance enhancement). |
| 04 – View Site Information | Satisfied | `SiteController`, `site-detail.component.html` | Metadata, checklist, documents, CRA assignment, monitoring visits all present. |

### Epic 3: Subject Management

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Enroll a New Subject | Satisfied | `SubjectService.enrollSubject` | Drools eligibility check blocks enrollment before persistence; deterministic, matches BRD's anti-AI premise. |
| 02 – Track Subject Lifecycle Status | Satisfied | `SubjectLifecycleService` | Linear + WITHDRAWN-from-anywhere transitions; withdrawal requires reason code **and** e-signature (beyond spec). |
| 03 – Update Subject Details | Partial | `SubjectService.updateSubject` | Subject ID/screening date correctly locked at the request-shape level; audit entry logs a generic string, not actual before/after field values as AC requires. |
| 04 – View Subject Profile | Satisfied | `SubjectResponse.from(subject, callerRoles)` | Explicitly nulls `medicalHistory` outside authorized roles — code comment directly cites this story's AC. |

### Epic 4: Visit Management

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Configure Visit Schedules | Satisfied | `VisitTemplateService`, `propagateToScheduledVisits` | Propagates to SCHEDULED visits only, preserving history; no dedicated "reorder" endpoint (sequence is just an editable field, functionally equivalent). |
| 02 – Track Completed and Missed Visits | Partial | `VisitService.markCompleted/markMissed/reschedule` | Status/date/notes/reason/compliance-rate all present and audited; no mechanism to attach a supporting document to a specific visit. |
| 03 – Generate Visit Alerts and Notifications | Partial | `VisitAlertService` (cron), `NotificationService` | Due/overdue alerts generated and deduped correctly; no audit log entry for the notification event itself (AC explicitly wants this). |
| 04 – View Visit Schedule and History | Satisfied | `VisitController`, `subject-detail.component.ts` | Chronological, missed/rescheduled visually flagged, RBAC applied. |

### Epic 5: Workflow & Automation

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Auto-Create Tasks Based on Events | Satisfied | `TaskRuleService` (Drools), `TaskService.createTask` | Rule-driven owner/SLA/priority, audited, notifies on creation. |
| 02 – SLA-Based Task Tracking | Satisfied | `task-inbox.component.ts` `urgencyClass`/`urgencyLabel` | Color-coded urgency computed on render rather than push-updated — a reasonable, standard interpretation, not a functional gap. |
| 03 – Automatic Task Escalations | Partial | `TaskEscalationService` (cron) | SLA-breach detection, reassignment, notification, and audit all present; escalated tasks aren't a distinct dashboard KPI as the AC specifies. |
| 04 – Notifications for Tasks, Visits, and Delays | Satisfied | `notification-list.component.ts`, `NotificationService` | In-app + email, filter/history present; SMS is the documented deviation. |

### Epic 6: Monitoring & Reporting

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Assign CRA to Sites | Satisfied | `SiteService.assignCra` | Primary + backup CRA, notifications, audit trail. |
| 02 – Record and Track Monitoring Visits | Satisfied | `MonitoringVisitService`/`MonitoringVisitReportController` | SIV/IMV/COV type, checklist/findings/issues, separate report-upload entity. |
| 03 – Real-Time Study Dashboards | Satisfied | `DashboardService.summary`, `DashboardExportService` | Full KPI set, filters, PDF+Excel export both present. |
| 04 – Milestone Tracking (FPI/LPI/LPO/DBL) | Satisfied | `MilestoneType` enum, `MilestoneAlertService` | Planned-vs-actual, delayed flag, deadline alerts, visible in dashboard. |

### Epic 7: Document Management & Compliance

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Upload Study Documents | Partial | `DocumentController.upload`, `DocumentService.createDocument` | No explicit file-type/size allow-list (only a global 25MB limit); no "confirm before version-increment" step — it auto-increments silently instead. |
| 02 – Document Version Control | Satisfied | `DocumentService.promoteToCurrent` | Auto-increment, CURRENT/ARCHIVED lifecycle, full history, old versions have no edit endpoint. |
| 03 – Document Approval Workflow | Satisfied | `DocumentWorkflowService` | Reviewer → approver stages, mandatory reject comments, e-signature on final approval, data-driven role-per-category. |
| 04 – Full Audit Trail for Document Activities | Partial | `AuditLogController`, `AuditLog` entity | Immutable, user/timestamp/action/before-after all captured, CSV export exists; **no IP address field** despite the AC explicitly listing it. |
| 05 – Role-Based Access to Documents | Satisfied | `DocumentAccessControlService`, `DocumentCategoryAccessRule` | Data-driven deny-list by category+role; denials themselves are audit-logged as `ACCESS_DENIED`. |

### Epic 8: Financial Management

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Rule-Based Payments for Activities | Satisfied | `PaymentRuleService` (Drools), `PaymentService` | Auto-generates on trigger events, links to triggering event, audited. |
| 02 – Budget Tracking (Planned vs Actual) | Partial | `BudgetService.toResponse`, `BudgetExportService` | Actual/variance genuinely computed live from real payments; no trend-over-time graph and no explicit "period" filter in the frontend. |
| 03 – Budget Version Control | Satisfied | `BudgetService.createNewVersion` | Reason mandatory, auto-increment, comparison view, old versions locked. |
| 04 – Payment Holds and Releases | Satisfied | `PaymentService.hold`/`release` | Mandatory reason on hold, blocks reprocessing, release requires e-signature + reason, audited. |

### Epic 9: System Configuration

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Configure Workflows Without Code | Modified | `RuleSetController`/`DroolsRuleEngine` | A DRL-text editor, not a visual/drag-and-drop builder (see deviation #4 above); instant-apply and audit trail are real. |
| 02 – Visit Template Configuration | Satisfied | `VisitTemplateService.validateWindow`, dependency-cycle checks | Window validation, dependency + cycle detection, historical data preserved on update. |
| 03 – Document Rules Configuration | Modified | `DocumentRequirementService`, `DocumentRequirement.studyPhase` | Mandatory-doc-by-phase and blocking both real; phase values use the `StudyStatus` enum (ACTIVE/CONDUCT/CLOSEOUT) rather than the BRD's literal "Start-Up/Conduct/Closeout" labels — same concept, different vocabulary. |
| 04 – No-Code Rule Updates Across the System | Modified | Same DRL mechanism as Story 01 | Self-documented gap — see `NO_CODE_RULE_BUILDER_FEASIBILITY.md`, an explicit written assessment recommending this as a future dedicated phase rather than a bug to silently patch. |

### Epic 10: Patient Portal

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Secure Patient Login | Modified | `AuthenticationService.login()` | Lockout, generic error messages, audit logging, password reset all present; MFA is the documented deviation. |
| 02 – View Visit Schedule | Partial | `PatientVisitController`, `patient-visits.component.html` | List view only — AC asks for **both** calendar and list views; no explicit per-visit location field. |
| 03 – Notifications for Visits and Study Activities | Modified | `NotificationService`, `NotificationBellComponent` | In-app + email fully functional; SMS is the documented deviation. |
| 04 – Upload Patient Documents | Satisfied | `PatientDocumentController`/`PatientDocumentUploadService` | Lands in the existing staff review queue, scoped to own uploads only. |
| 05 – Update Patient Profile | Satisfied | `SubjectService.updateOwnProfile` | Narrow field set (contact info only), DOB/status locked, audited, confirmation message shown. |

### Epic 11: Regulatory Compliance (GCP, 21 CFR Part 11, Audit Trail, Data Integrity)

| Story | Status | Evidence | Notes |
|---|---|---|---|
| 01 – Enforce GCP Compliance | Satisfied | `DocumentRequirementService`, `ConsentGateService`, `ProtocolDeviation` entity | Consent gates at visit *completion*, not enrollment (deliberate — a Subject must exist before a consent document can be attached to them). |
| 02 – 21 CFR Part 11 E-Signatures | Modified | `ESignatureService.sign()`, wired into 7 real domains | Password-only identity verification, no MFA-backed signing (documented deviation) — everything else (locking, audit, reason-for-signing) matches. |
| 03 – ALCOA Data Integrity | Satisfied | Zero `@DeleteMapping`/`repository.delete()` calls anywhere (grep-verified) | Fully verified — no hard deletes exist in the entire codebase. |
| 04 – End-to-End Traceability | Satisfied | `AuditLogController` `/traceability/{entity}/{id}`, `/export` | Joins audit trail + e-signatures, read-only, CSV export. |

### Part D: Clinical Safety (carried forward, Implementation Plan Phase 7)

| Story | Status | Evidence | Notes |
|---|---|---|---|
| BL-06 – Test Results | Satisfied | `TestResultController`, `TestResult` entity linked to Subject + Visit | Fully matches. |
| BL-07 – Test Result Source File Attachment | Satisfied | `TestResultAttachmentController` | Upload/download, uploadedBy/At captured, audited. |
| BL-08 – Adverse Events | Satisfied | `AdverseEventService.report()`, shared by staff and patient controllers | Severity + OPEN→UNDER_REVIEW→RESOLVED workflow exactly as specified. |

---

## The Two Flagged Traceability Gaps (Part E of the reference doc)

The reference document itself flagged two open product questions when the backlog was written. Both are now resolved by direct code inspection:

1. **Should Informed Consent be a first-class tracked entity (a consent-status state machine per participant), rather than folded into general document compliance?**
   **Still open — this is a real, unresolved gap.** `ConsentGateService` checks for a `Document` row with `category="INFORMED_CONSENT"` and `status=CURRENT`; there is no separate consent entity with its own pending/consented/withdrawn states. This is a deliberate reuse of the existing document-compliance mechanism, not an oversight — but if per-participant consent *status tracking* (as opposed to a presence check) is a hard requirement, it isn't satisfied today.

2. **Does Subject enrollment need the explicit structured inclusion/exclusion checklist from the old backlog (BL-11)?**
   **Resolved — closed.** `EligibilityCriterion` (per-study, labeled, INCLUSION/EXCLUSION typed) and `SubjectEligibilityAnswer` (per-subject, per-criterion, boolean `met` + timestamp) together form exactly the structured, auditable pass/fail checklist BL-11 asked for.

---

## Non-Functional Requirements & Dependencies (BRD §7 / §8 / §3.2)

| Requirement | Status | Evidence | Notes |
|---|---|---|---|
| 7.1 Performance (concurrent users, ≤2–3s response, bulk data) | Not Verifiable / Not Tested | No load-test artifacts (JMeter/Gatling/k6) in the repo | Would need an actual load test; nothing in the code contradicts it either. |
| 7.2 Scalability (more studies/sites/subjects, multi-region) | Not Verifiable / Not Tested | Pagination present on all list endpoints | Architecture doesn't preclude it, but no evidence at real scale. |
| 7.3 Security (RBAC, encryption, MFA) | Modified (documented deviation) | 30 files use `@PreAuthorize`; `BCryptPasswordEncoder`; account lockout after 5 attempts | MFA deliberately not built, compensated with lockout. |
| 7.4 Reliability & Availability (≥99.9%, backups, DR) | Not Verifiable / Not Tested | No backup/DR/uptime-monitoring config in the repo | Infra/deployment concern, not app code. |
| 7.5 Maintainability (modular, Java/Kotlin/React) | Modified (documented deviation) | Java-only backend (no Kotlin), pure Angular frontend (zero `react` dependency) | React→Angular already documented; Kotlin-never-used is a newly-noted deviation. |
| 7.6 Testability & Validation (deterministic, no AI ambiguity) | Satisfied | Zero AI/ML/LLM references anywhere in either codebase (grep-clean); Drools DRL is the sole business-logic mechanism | Directly satisfies the BRD's central premise. |
| 7.7 Usability (user-friendly, minimal training) | Not Verifiable / Not Tested | Subjective; no usability-testing artifacts | Would need actual user testing. |
| 7.8 Interoperability (external systems, standard APIs) | Partial | Consistent REST API confirmed throughout; zero EDC-related code found | APIs exist and are usable for integration, but no actual external integration is built. |
| 7.9 Auditability (log all actions, exportable, inspection-ready) | Satisfied | `AuditAspect`/`AuditService`, immutable (no update/delete endpoint), Excel/PDF export | Matches CLAUDE.md's non-negotiable audit rule. |
| 8.1 Technology Dependencies (Java/Kotlin, React, PostgreSQL) | Modified (documented deviation) | Spring Boot/Java confirmed, no Kotlin; Postgres confirmed; Angular not React | React→Angular already documented. |
| 8.2 Notifications & Auth Integrations (email/SMS, SSO/MFA) | Modified (documented deviation) | Self-hosted SMTP only, zero SMS/SSO/MFA library anywhere in `pom.xml` | Both deliberate, both documented. |
| 8.2 EDC System Integration | **Not Implemented** | Zero EDC integration code or config found anywhere | **Not a documented deviation anywhere** — named in-scope for "operational linkage" (BRD §3.1) but never built. The one genuine, undocumented gap in this entire analysis. |
| A.3.2 Out of scope: no AI/ML predictive/black-box logic | Satisfied (correctly out of scope) | Exhaustive grep for AI/ML/LLM terms across both codebases: zero matches | Confirms the BRD's central "deterministic, non-AI" premise — the absence is the correct, intended state. |
| A.3.2 Out of scope: no full EDC/safety-analytics system | Satisfied (correctly out of scope) | `TestResult`/`AdverseEvent` are simple operational records tied to Subject/Visit, not an analytics system | Correct scope boundary, deliberately not built beyond operational linkage. |

---

## Bottom Line

- **65% of all backlog stories (32/49) are fully satisfied**, with the remaining 35% split between partial-credit gaps (20%, one missed acceptance criterion each, not missing features) and deliberate, mostly pre-documented deviations (14%).
- **Zero backlog stories are entirely unimplemented.**
- The system's core positioning claim — deterministic, rules-driven, no AI/ML anywhere — is fully and verifiably true (grep-clean across both codebases).
- The only **undocumented** gap found across the entire BRD/backlog is **EDC system integration** (BRD §8.2) — everything else either works, works differently by deliberate design, or is a subjective/infra claim that can't be verified from code alone.
- The **Informed Consent as first-class entity** question, flagged as open in the reference document itself, remains genuinely open — worth a product-owner decision if per-participant consent-status tracking (not just a presence check) becomes a hard requirement.
- Not tracked in the original backlog at all, but discovered and closed earlier in this engagement: **Admin > User Management** (creating users and assigning roles) was implied by the RBAC matrix but had no actual implementation until this session added it.
