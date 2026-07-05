# Clinical Trial Management System (CTMS) — Phased Feature Implementation Plan
**Project:** Pharma CTMS (per `Pharma_BRD_V0_1.pdf` & `pharma_problemStatement_V0_2.pdf`)
**Tech Stack:** Java Spring Boot (backend) · Angular + Tailwind CSS (frontend) · PostgreSQL
**Backlog Source:** `Product_Backlog_0_2__1_.xlsx` (46 stories / 11 epics) + Clinical Safety epic carried forward from `Product_Backlog_based_on_review.xlsx` (per your confirmation)

> **Supersedes** the earlier "LSHCERV Phased Implementation Plan" — that was built against the wrong documents (a warehouse medicine-expiry BRD). This plan replaces it entirely.

---

## 0. What the Documents Actually Say

**Business Objective (BRD §1):** Replace manual, spreadsheet/email-driven clinical trial operations with a **deterministic, rules-driven CTMS** that *actively manages* trial workflows (study, site, subject, visit, document, task, financial) rather than passively recording data — explicitly **avoiding AI/ML black-box decision-making** in favor of transparent, configurable, auditable rules.

**Problem Statement:** Current trial execution is manual/fragmented, error-prone, hard to track in real time, compliance-sensitive, and poorly scalable. Existing CTMS platforms are seen as over-engineered with AI features that are costly to validate — the ask is a **rules-driven, configuration-based** system instead.

**In Scope (BRD §3):** Study & Site Management, Subject & Visit Management, Workflow & Task Automation, Monitoring & Oversight (CRA, milestones FPI/LPI/DBL), Document & Compliance Management, Financial Management, Reporting & Transparency, Patient Portal, Configuration & Validation.

**Out of Scope (BRD §3):** Advanced AI/ML predictive analytics or black-box decision-making; full clinical data analysis (EDC), safety systems, or lab data management *beyond operational linkage* — i.e., the BRD wants AE/lab results **linked operationally**, not a full EDC/safety database. This aligns with building AE/Test-Result **tracking and linkage**, not a clinical data-analysis platform.

**Explicit Tech Dependencies (BRD §8.1):** Backend Java/Kotlin, Frontend React, DB PostgreSQL. *Per your instruction, this plan uses Angular (with Tailwind CSS and Angular Material where useful) instead of React for the frontend — noting the deviation from the BRD's own stated stack.*

**Key NFRs (BRD §7):** concurrent multi-site/multi-study usage, response time ≤2–3s, scale across studies/sites/subjects, RBAC + encryption + MFA, 99.9% availability, modular/maintainable architecture, deterministic/testable logic, standard APIs for EDC interoperability, full auditability with exportable audit reports. *Per your organizational constraints, this plan does not implement OTP-based MFA or any third-party/external API services (SMS gateways, OTP providers, etc.) — see §1 and the Open Items in §5 for how security/notification NFRs are met instead.*

---

## 1. Tech Stack & Architecture

### Backend — Java Spring Boot
| Concern | Component |
|---|---|
| Web layer | Spring Boot 3.x, REST controllers, springdoc-openapi |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL |
| Migrations | Flyway |
| Security | Spring Security + JWT (access/refresh), strong password policy (complexity, expiry, history), account lockout after repeated failed attempts, session/token expiry controls, `@PreAuthorize` RBAC — **no OTP/MFA, no external identity providers** |
| Rules/Workflow engine | **Drools** (or a lightweight custom JSON-rule interpreter) embedded in Spring Boot — this is what makes "no-code" workflow/visit/document rule configuration (Epic: System Configuration) possible without hardcoding logic per study |
| Scheduling | Spring `@Scheduled` / Quartz — visit-window checks, SLA breach detection, milestone alerts, escalations |
| Audit & e-signature | Hibernate Envers or custom `AuditLog` + `ESignature` entity (user, **password re-auth only** — no second factor —, reason-for-signing, timestamp) for 21 CFR Part 11 |
| Notifications | Spring Mail (email, self-hosted/internal SMTP) + in-app `Notification` entity — **no SMS, no third-party notification APIs** |
| File storage | `StorageService` interface (local/S3), versioned `Document` entity, checksum on upload |
| Reporting/Export | Server-side CSV/PDF/Excel generation (Apache POI / OpenPDF) |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres) |

### Frontend — Angular + Tailwind CSS (+ Angular Material where useful)
| Concern | Component |
|---|---|
| Framework | Angular standalone components, Reactive Forms, Angular Router |
| Styling | Tailwind CSS for layout/utility styling; **Angular Material** for complex interactive components where it saves effort (data tables with sort/filter/pagination, date pickers, dialogs/modals, stepper for lifecycle transitions, snackbars for toasts, autocomplete). Tailwind still handles page layout, spacing, and custom-branded surfaces. Use Material's theming API (not competing CSS) to keep the two systems from fighting each other — define one Material theme aligned to the Tailwind design tokens (colors/spacing) so the UI looks cohesive |
| No-code builder UI | Angular CDK **drag-drop** (available standalone, or via Material which is built on CDK) for the workflow/visit-template/document-rule configuration screens |
| State | RxJS services / Angular Signals; introduce a store (e.g. NgRx) only if Phase 10 (System Configuration) proves state complexity warrants it |
| Charts/Dashboards | Chart.js wrapper for KPI widgets, milestone timelines, enrollment funnels |
| Auth | JWT interceptor, route guards, role-based directive — **no MFA challenge step** |
| Patient Portal | Same Angular workspace, separate route module + role-scoped shell (simplified nav, mobile-friendly layout) |

### Cross-Cutting Platform Services (built once in Phase 0, reused everywhere)
- **AuthN/AuthZ** — login, JWT, password policy, account lockout, RBAC roles (single-factor: username/password only)
- **Audit Log** — generic who/what/when/before-after, immutable, exportable
- **E-Signature** — reason-for-signing capture, password re-authentication, record locking (21 CFR Part 11)
- **Notification** — in-app + email only, event-driven (no SMS/external gateway)
- **Document/File** — upload, versioning (no-overwrite), RBAC-gated access
- **Rules/Config Engine** — the mechanism that makes visit templates, document requirements, and workflow logic editable without code

### Architectural Decision — Design for Configurability from Day One
Several epics (Visit Management, Document Management, Workflow & Automation) assume their *rules* are configurable (Epic: System Configuration, stories 01–04). Rather than hardcoding visit windows / document requirements / task-trigger logic per study and refactoring later, **Phases 1–8 should store these as data driven by the rules engine from the start**, even before the no-code *admin UI* for editing them exists. Phase 10 (System Configuration) then delivers the UI on top of an already-configurable backend, instead of retrofitting configurability into hardcoded logic.

### Architectural Decision — Security & Notifications Without MFA or External APIs
Per organizational constraint, this system will **not** use OTP-based MFA or any external/third-party API (SMS gateways, OTP providers, identity providers, etc.). Practical implications carried through the plan:
- **Authentication** is single-factor: username/password over Spring Security + JWT, hardened with strong password policy, account lockout after repeated failed attempts, and short-lived tokens with refresh — compensating controls rather than a second factor.
- **Notifications** are limited to **in-app + email** (via internal/self-hosted SMTP, not a third-party transactional-email API). Any story referencing SMS (Visit alerts, Task notifications, Patient Portal notifications) is scoped down to in-app + email only.
- **E-signatures (21 CFR Part 11, Phase 12)** rely on password re-authentication plus reason-for-signing capture, rather than MFA-backed signing. This is a legitimate implementation pattern in many validated systems, but **flag it explicitly to QA/Compliance stakeholders** during validation planning, since a single-factor e-signature is a more scrutinized control than a multi-factor one under some interpretations of Part 11 — better to document the accepted risk now than discover it during audit prep.
- **EDC / external-system integration (Phase 13)** still needs an outbound/inbound API contract — that's a required system integration, not a "third-party API service," so it isn't affected by this constraint. Worth confirming that distinction with stakeholders if there's any ambiguity.

---

## 2. RBAC Roles (Consolidated from BRD §4 + Backlog)

| Role | Primary Access |
|---|---|
| Admin | System configuration, user management, rule/workflow/document/visit config |
| Study Manager (CTM) | Study/site/subject lifecycle, document approvals, milestone tracking |
| Site Coordinator | Subject enrollment, visit updates, document upload at site level |
| Investigator | Site-level clinical oversight, consent, AE reporting |
| CRA / Monitor | Monitoring visit logging, site oversight, compliance follow-up |
| Data Management | Data quality views, EDC integration touchpoints |
| Finance Manager | Payment rules, budget tracking, holds/releases |
| QA / Compliance Officer / Auditor | Audit trail access, traceability reports, GCP/21 CFR Part 11 validation views |
| Clinical Project / Program Leadership | Portfolio dashboards, milestone/risk visibility |
| Executive | Real-time dashboards, cross-study analytics |
| Sponsor / CRO Leadership | Reporting exports, oversight views (often read-only) |
| Patient / Subject | Patient Portal only — own visits, tasks, documents, notifications |
| Regulatory Authority (external) | Not a system login — consumes exported audit/compliance packages |

---

## 3. Phase Plan Overview

| Phase | Focus | Epic(s) / Source | Est. Sprints* |
|---|---|---|---|
| 0 | Platform Foundation | — | 2 |
| 1 | Study Management | Backlog Epic 1 | 1 |
| 2 | Document Management & Compliance | Backlog Epic 7 | 2 |
| 3 | Site Management | Backlog Epic 2 | 2 |
| 4 | Subject Management | Backlog Epic 3 | 1 |
| 5 | Visit Management | Backlog Epic 4 | 2 |
| 6 | Workflow & Automation | Backlog Epic 5 | 2 |
| 7 | **Clinical Safety — Test Results & AE** *(new, added per your decision)* | Carried from BL-06/07/08 | 2 |
| 8 | Monitoring & Reporting | Backlog Epic 6 | 2 |
| 9 | Financial Management | Backlog Epic 8 | 2 |
| 10 | System Configuration (no-code admin UI) | Backlog Epic 9 | 2 |
| 11 | Patient Portal | Backlog Epic 10 | 2 |
| 12 | Regulatory Compliance Hardening | Backlog Epic 11 | 2 |
| 13 | UAT, Integration Testing, Go-Live | — | 2 |

*\*Assuming 2-week sprints and a team velocity of ~20 story points/sprint; totals ~26 sprints (~12 months). Recalculate against your actual team velocity.*

---

## Phase 0 — Platform Foundation *(≈2 sprints)*

| Feature | Backend | Frontend |
|---|---|---|
| Auth | JWT issuance/refresh, password policy (complexity/expiry/history), account lockout on repeated failures — single-factor, no MFA | Login screen, forgot/reset password flow |
| RBAC | Roles per §2, `@PreAuthorize` on all endpoints from day one | Route guards, role directive, role-based nav |
| Audit Log framework | Generic `AuditLog` entity + AOP aspect on state-changing methods; immutable, exportable | Audit log viewer (Admin/QA) |
| E-signature primitive | `ESignature` entity: user, password re-auth (no second factor), reason-for-signing, timestamp, linked record | Reusable "Sign" modal component |
| Notification framework | `Notification` entity, event listener pattern, in-app + internal/self-hosted SMTP email — no SMS/external gateway | Notification bell + list, mark-as-read, preferences |
| Document/File service | Versioned `Document`/`DocumentVersion` entities, `StorageService` abstraction, checksum | Reusable upload/version-history component |
| Rules/config engine skeleton | Embed Drools (or custom JSON rule interpreter); define `RuleSet`/`RuleDefinition` schema | — |
| CI/CD & testing | Flyway baseline, GitHub Actions, Testcontainers | Angular build/lint/test pipeline |

**Exit criteria:** Login, RBAC, audit, e-signature primitive, notifications, and document/versioning plumbing are all unit-tested and ready for feature teams.

---

## Phase 1 — Study Management *(Backlog Epic 1, 4 stories, ≈19 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Create Study | `Study` entity (name, protocol ID [unique], phase, sponsor, protocol version, dates); validation; auto-generated Study ID; default state `DRAFT` | "Create Study" form with inline validation |
| 02 | Update Study Details | Field-level edit permissions driven by lifecycle state (e.g., protocol ID locked post-activation) | Edit form respecting locked fields |
| 03 | Manage Study Lifecycle States | State machine `DRAFT → ACTIVE → CONDUCT → CLOSEOUT`; prerequisite validation; justification/doc capture on transition; notifications on change | Lifecycle stepper UI with transition action + justification modal |
| 04 | View Study Details | Aggregated read view (overview, lifecycle, sites, subjects, documents, milestones); role-based field hiding | Study overview dashboard page |

**Exit criteria:** Studies can be created, edited within lifecycle rules, transitioned through states with audit trail, and viewed by all authorized roles.

---

## Phase 2 — Document Management & Compliance *(Backlog Epic 7, 5 stories, ≈34 pts)*

Built early because **Site activation (Phase 3) depends on document completion checks.**

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Upload Study Documents | Metadata capture (category, version, effective date, owner); file type/size validation; version-conflict handling | Upload form + repository list view |
| 02 | Document Version Control | Auto-increment version, archive previous, mark "Current," old versions read-only | Version history panel, diff/compare view |
| 03 | Document Approval Workflow | Reviewer → Approver routing; comments; lock on approval; rejection returns to uploader with mandatory comment | Approval queue, review/approve/reject actions |
| 04 | Full Audit Trail for Document Activities | Immutable log of upload/version/approval/rejection/deletion/access attempts, with before/after values | Read-only audit viewer, export button |
| 05 | Role-Based Access to Documents | Document-level RBAC (e.g., patients can't see PI docs; CRAs can't see financials) | Access-controlled repository views; masked/hidden restricted docs |

**Exit criteria:** A single, versioned, approval-gated, fully audited document repository exists — reused by Site prerequisites, Subject consent, and AE report attachments.

---

## Phase 3 — Site Management *(Backlog Epic 2, 4 stories, ≈24 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Register a New Site | `Site` entity (name, code [unique], location, PI details, contact, feasibility status); default `PENDING_ACTIVATION` | "Register Site" form |
| 02 | Track Site Activation Status | Activation checklist (feasibility, IRB/EC approval, contract, essential docs, SIV) with per-item date/status | Activation dashboard (green/red checklist) |
| 03 | Enforce Activation Prerequisites | Block activation if any required doc/milestone incomplete (queries Phase 2's document service); list missing items with deep links | Blocking modal listing missing prerequisites with jump-to links |
| 04 | View Site Information | Full site profile: metadata, timeline, documents, visit history, enrollment metrics, CRA assignment | Site overview page |

**Exit criteria:** Sites can't go active without satisfying document/approval prerequisites — enforced at the service layer, not just the UI.

---

## Phase 4 — Subject Management *(Backlog Epic 3, 4 stories, ≈19 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Enroll a New Subject | `Subject` entity, demographic capture, inclusion/exclusion eligibility validation, auto Subject ID, default state `SCREENED` | "Enroll Subject" screen with eligibility checklist |
| 02 | Track Subject Lifecycle Status | State machine `SCREENED → ENROLLED → IN_TREATMENT → COMPLETED / WITHDRAWN`; reason codes on certain transitions; invalid-transition guard | Lifecycle status control with reason-code modal |
| 03 | Update Subject Details | Editable vs. locked fields (Subject ID, original screening date locked); before/after audit | Subject edit form |
| 04 | View Subject Profile | Demographics, lifecycle, visit schedule/history, documents, notes, deviations | Subject profile page |

---

## Phase 5 — Visit Management *(Backlog Epic 4, 4 stories, ≈21 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Configure Visit Schedules | `VisitTemplate` entity (name, sequence, target day, early/late window, procedures, onsite/remote); auto-applies to new enrollments; propagation rules for updates that don't overwrite historical data | Visit template builder (uses the config-engine groundwork from Phase 0) |
| 02 | Track Completed & Missed Visits | Visit status update (completed/missed/rescheduled), mandatory reason code for missed, compliance-rate calculation | Visit status update form |
| 03 | Generate Visit Alerts & Notifications | Scheduled job: due-tomorrow / overdue / missed triggers, routed through Phase 0 notification service (email/in-app only) | Notification center integration |
| 04 | View Visit Schedule & History | Chronological visit history, deviations, missed visits, reschedule trail | Calendar + list views, role-scoped |

---

## Phase 6 — Workflow & Automation *(Backlog Epic 5, 4 stories, ≈26 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Auto-Create Tasks Based on Events | Event listeners on enrollment/activation/visit-completion/etc. → auto-generate `Task` (owner, due date, priority) via the rules engine | Task inbox, auto-populated |
| 02 | SLA-Based Task Tracking | SLA countdown, color-coded urgency, dynamic status updates | Task cards with urgency badges |
| 03 | Automatic Task Escalations | Detect SLA breach → escalate to predefined user, optional reassignment, log escalation history | Escalation banner/alert in dashboards |
| 04 | Notifications for Tasks, Visits, and Delays | Consolidate all trigger points into the shared notification service, with filter/history view | Notification center: filters + history |

**Exit criteria:** This is the automation backbone — from here on, AE escalation, milestone alerts, and document-expiry reminders all reuse this engine rather than building bespoke logic.

---

## Phase 7 — Clinical Safety: Test Results & Adverse Events *(New epic — added per your decision, carried from BL-06/07/08)*

Not present in backlog v0.2; reintroduced because safety tracking is core to a clinical trial system and the BRD explicitly wants safety data **operationally linked** (even though full EDC/safety-database scope is out of scope).

| Ref | Feature | Backend | Frontend |
|---|---|---|---|
| BL-06 | Record & Review Test/Lab Results | `TestResult` entity linked to Subject + Visit; result status tracking | Result entry form + review list, filter by subject/visit |
| BL-07 | Attach Source Lab Report | `TestResultAttachment` (reuses Phase 2 `StorageService`), download, audit-logged | Upload/download UI on the result record |
| BL-08 | Adverse Event Reporting | `AdverseEvent` entity: severity levels, status workflow `OPEN → UNDER_REVIEW → RESOLVED`, reportable-by participant or investigator; escalation for high-severity AEs routed through Phase 6's task/notification engine | AE report form (Investigator/Site Coordinator, and Patient Portal in Phase 11), AE tracking board (Kanban by status/severity) |

**Recommendation:** Confirm story sizing/priority with the same stakeholders who own backlog v0.2, since these were carried over without re-estimation — the point values aren't part of the official v0.2 backlog.

---

## Phase 8 — Monitoring & Reporting *(Backlog Epic 6, 4 stories, ≈26 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Assign CRA to Sites | Primary/backup CRA assignment per site, reflected instantly in monitoring scheduling | CRA assignment screen |
| 02 | Record & Track Monitoring Visits | `MonitoringVisit` entity (type SIV/IMV/COV, checklist, issues, uploaded report via Phase 2 doc service) | Monitoring visit log, chronological history |
| 03 | Real-Time Study Dashboards | Aggregation service: enrollment, activation, compliance, visit adherence, country/site breakdowns, risk flags | Dashboard with filters (study/region/site/phase), export (PDF/Excel) |
| 04 | Milestone Tracking (FPI, LPI, LPO, DBL) | Planned-vs-actual milestone entity, delay flagging, stakeholder alerts near deadlines | Milestone timeline view (red-flagged delays) |

---

## Phase 9 — Financial Management *(Backlog Epic 8, 4 stories, ≈26 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Rule-Based Payments for Activities | Payment rule engine (conditions, multipliers, caps, currency) triggered by visit/milestone events; links payment to triggering activity | Site financial ledger view |
| 02 | Budget Tracking (Planned vs. Actual) | Aggregation across cost categories (investigator fees, monitoring, labs); variance calculation | Budget dashboard with drill-down, trend graphs |
| 03 | Budget Version Control | Versioned budget snapshots, timestamp/user/reason, comparison view, old versions read-only | Version comparison UI |
| 04 | Payment Holds & Releases | Hold with mandatory reason code; blocks processing until released | Hold/release action with reason modal |

---

## Phase 10 — System Configuration (No-Code Admin UI) *(Backlog Epic 9, 4 stories, ≈26 pts)*

Builds the **editing UI** on top of the rules-engine groundwork laid in Phase 0 and consumed by Phases 3/5/6.

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Configure Workflows Without Code | Visual workflow step/condition/escalation editor validated against workflow-consistency rules (no orphan steps, valid transitions) before going live | Drag-and-drop workflow builder (Angular CDK) |
| 02 | Visit Template Configuration | Extends Phase 5's `VisitTemplate` with dependency rules between visits; non-destructive updates to historical data | Visit template editor UI |
| 03 | Document Rules Configuration | Mandatory-document-by-phase rules consumed by Phase 3's activation-prerequisite check | Document rule matrix editor |
| 04 | No-Code Rule Updates Across the System | Unified rule editor spanning workflow/visit/document/notification/payment rules; live activation, no downtime | Central "Rules" admin section |

---

## Phase 11 — Patient Portal *(Backlog Epic 10, 5 stories, ≈21 pts)*

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Secure Patient Login | Credential auth (single-factor), rate-limited login attempts, account lockout, password reset via email link | Patient login flow (separate lightweight shell) |
| 02 | View Visit Schedule | Read access to Phase 5 visit data, scoped to own subject record | Calendar + list view |
| 03 | Notifications for Visits & Study Activities | Reuses Phase 0/6 notification service, patient-scoped | Notification center (patient view) |
| 04 | Upload Patient Documents | Reuses Phase 2 document service, patient-facing upload with simplified metadata | Upload UI |
| 05 | Update Patient Profile | Editable contact/demographic fields; sensitive fields locked | Profile edit form |

*Consider also surfacing AE self-reporting (Phase 7) and consent status here, since the BRD's Patient Portal description explicitly includes self-management and reduced dependency on site staff.*

---

## Phase 12 — Regulatory Compliance Hardening *(Backlog Epic 11, 4 stories, ≈32 pts)*

Cross-cutting; sits last because full end-to-end traceability needs every module above to exist first.

| Story | Feature | Backend | Frontend |
|---|---|---|---|
| 01 | Enforce GCP Compliance | Validate essential-document presence, consent-before-activity checks, deviation logging, GCP-aligned monitoring-visit structure | Compliance dashboard flags |
| 02 | 21 CFR Part 11 (E-Records & Signatures) | Harden Phase 0's `ESignature` primitive: identity verification, record locking post-signature, non-repudiation | E-signature flow across all sign-required actions |
| 03 | ALCOA Data Integrity | Enforce Attributable/Legible/Contemporaneous/Original/Accurate on every write: user identity + timestamp on all records, reason-coded edits, versioning, no hard deletes | Edit-reason capture modal, version diff views |
| 04 | End-to-End Traceability | Cross-entity traceability report generator (every field/document's creation, modification, signature, approval chain) | Traceability report screen + export |

**Exit criteria:** This is the audit/inspection-readiness milestone — a natural **go-live gate**.

---

## Phase 13 — UAT, Integration Testing & Go-Live

- End-to-end regression across all 12 feature phases
- Load testing against NFR targets (≤2–3s response, concurrent multi-site/multi-study load)
- Security review: RBAC matrix walkthrough, password/lockout policy, encryption at rest/in transit, audit-log completeness
- Stakeholder UAT: Study Managers, CRAs, Site Coordinators, QA/Compliance, Finance, Executives, sample patients
- EDC/external-system integration test pass (Interoperability NFR §7.8)
- Training materials + admin runbook
- Go-live cutover plan, including legacy spreadsheet/data migration

---

## 4. Key Risks Carried Into the Plan (from BRD §9)

| Risk | Phase(s) Mitigated | Mitigation |
|---|---|---|
| Complex workflow/rule misconfiguration | 6, 10 | Rules-engine validation before activation; staged rollout; sandbox testing |
| Integration failure (EDC, finance, external systems) | 13 | Standard API contracts defined early (Phase 0/8), early interface testing |
| Data quality (inaccurate protocol/subject/site input) | 1, 3, 4 | Field validation, uniqueness checks, migration QA |
| User adoption resistance | 13 | Training, simple Tailwind UI, phased rollout, involve users during config design |
| Security & compliance risk (breach, non-compliance) | 0, 12 | RBAC + encryption + strong password/lockout policy from Phase 0 (compensating for the absence of MFA per org constraint); GCP/21 CFR Part 11/ALCOA hardening in Phase 12 |
| Configuration risk (wrong rules → workflow errors) | 10 | Config validation layer, versioned rule changes, audit trail on every rule edit |
| Scalability risk (multi-study/site/region growth) | 0, 8 | Stateless services, indexed queries, load testing in Phase 13 |
| Validation risk (delays in system validation/regulatory approval) | 12, 13 | Deterministic (non-AI) logic by design; full traceability built-in, not bolted on |
| Financial risk (incorrect payment rules) | 9 | Rule-based payment engine with audit trail, budget version comparison |

---

## 5. Open Items to Confirm With Stakeholders

1. **Frontend stack deviation:** BRD §8.1 specifies React; this plan uses Angular (Tailwind + Angular Material) per your instruction. Worth a documented decision record if this BRD is used for regulatory/validation sign-off.
2. **Clinical Safety epic (Phase 7):** story sizes/priorities were carried over from the older backlog, not re-estimated against v0.2's scale — recommend a sizing session with the product owner.
3. **No MFA / no external APIs — compliance sign-off:** the BRD's NFR §7.3 explicitly names MFA support, and 21 CFR Part 11 signing is generally stronger with a second factor. Per your organizational constraint, this plan uses single-factor auth + password re-authentication for e-signatures instead. Recommend getting written sign-off from QA/Compliance stakeholders that this is an accepted risk before Phase 12 validation planning, so it isn't raised as a finding late.
4. **Notification channel reduced to email + in-app only:** any backlog story mentioning SMS (Visit alerts, Task notifications, Patient Portal notifications) has been scoped down accordingly in this plan — confirm this matches expectations for site staff and patients who may be relying on SMS in current manual processes.
5. **EDC integration:** BRD explicitly excludes full clinical data analysis but wants "operational linkage" — the exact integration contract (real-time API vs. batch) should be defined before Phase 13. This is a legitimate system-to-system integration and isn't affected by the "no external APIs" constraint, but worth confirming that distinction explicitly with stakeholders.

---

*This plan is built directly from `Pharma_BRD_V0_1.pdf`, `pharma_problemStatement_V0_2.pdf`, and `Product_Backlog_0_2__1_.xlsx` (46 stories/11 epics), with the Clinical Safety epic reintroduced from `Product_Backlog_based_on_review.xlsx` per your confirmation.*
