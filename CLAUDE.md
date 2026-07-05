# CLAUDE.md — CTMS Project Guide

This file is read automatically at the start of every session. It exists because this is a **regulated clinical-trial system**, not a generic CRUD app — several of the rules below aren't style preferences, they're compliance requirements (GCP / 21 CFR Part 11 / ALCOA) that the BRD explicitly calls out. Violating them isn't a lint warning, it's a validation finding.

---

## 1. Required Reading Before Writing Code

Before implementing any feature, check these two files (same repo, `/docs`):

- **`CTMS_Requirements_Reference.md`** — full transcribed BRD, Problem Statement, and 46-story backlog (Parts A–F). If you're unsure what a field/rule/acceptance-criterion should be, this is the source of truth, not assumption.
- **`CTMS_Phased_Implementation_Plan.md`** — the phase order and dependency reasoning (e.g., why Document Management is built before Site Management). **Do not reorder phases** without flagging it — later phases assume earlier ones exist (System Configuration assumes a working rules engine skeleton; Workflow & Automation assumes Study/Site/Subject/Visit events already fire).

When a story number is referenced in a commit, PR, or code comment (e.g. `Epic 4 Story 02`), it maps directly to a section in the reference doc — use `grep` on it rather than re-deriving requirements from memory.

---

## 2. Non-Negotiable Domain Rules

These apply to every phase, every entity, every endpoint. If a task seems to require breaking one of these, stop and flag it rather than working around it.

### 2.1 No AI/ML, ever
The BRD's central premise is a **deterministic, rules-driven** system, explicitly positioned against "AI black-box" CTMS competitors (Problem Statement, BRD §1/§3 Out of Scope). Do not introduce:
- Predictive/statistical models, fuzzy matching, or "smart" heuristics for business logic (eligibility, scheduling, escalation, payments).
- LLM calls for anything that affects trial data, workflow decisions, or compliance records.
All business logic must be traceable to an explicit, human-readable rule. If a rule engine (Drools or the custom JSON interpreter) is used, every rule must be inspectable and explainable — that inspectability *is* the feature.

### 2.2 No MFA, no OTP, no third-party/external APIs
Per organizational constraint (not a BRD requirement — the BRD's own NFR §7.3 actually asks for MFA, so this is a documented deviation, see `CTMS_Requirements_Reference.md` Part A.7.3):
- Auth is single-factor (Spring Security + JWT), hardened with password policy + account lockout instead.
- No SMS gateways, no OTP libraries, no external identity providers (no Auth0/Okta/Firebase/Google/social login), no third-party transactional email APIs (SendGrid, Twilio, etc.). Email goes through internal/self-hosted SMTP only.
- If a task seems to need one of these, don't silently add it — flag it. This has already tripped up the plan once; don't reintroduce it via a "helpful" library suggestion.
- **Exception:** EDC/finance system integration (Phase 13) is a legitimate internal system-to-system contract, not a third-party API service — that's fine.

### 2.3 Every state-changing operation is audit-logged
Not optional, not "add it later." BRD §7.9, §9.8 (Validation Risk: "Incomplete audit trails → compliance and traceability issues"), and Epic 7/11 stories all require this.
- Use the shared `AuditLog` aspect (Phase 0) on every service method that creates, updates, deletes, approves, rejects, or transitions state — don't hand-roll logging per feature.
- Every audit entry captures: user identity, timestamp, action, entity type + ID, and old/new values for updates.
- Audit logs are immutable — no update/delete endpoint should ever exist for `AuditLog` records.

### 2.4 No hard deletes — ever
ALCOA's "Original" principle (BRD Epic 11 Story 03) means original data is never destroyed.
- Every entity gets a soft-delete / status flag (e.g., `ARCHIVED`, `WITHDRAWN`, `SUPERSEDED`) instead of a `DELETE FROM`.
- Anything versionable (documents, budgets, visit templates, workflow rules) follows the version-and-archive pattern established in Epic 7 Story 02: increment version, mark previous `CURRENT → SUPERSEDED`, keep it queryable and read-only.
- Edits to already-saved records require a reason code where the backlog specifies it (visit reschedule, subject withdrawal, payment hold/release, budget change) — don't skip the reason-code field because it's "just a string."

### 2.5 E-signature pattern for compliance-gated actions
21 CFR Part 11 (Epic 11 Story 02): approvals, disposal/closeout-type transitions, and document sign-off need password re-authentication + reason-for-signing, not just a button click.
- Reuse the shared `ESignature` primitive (Phase 0) rather than building bespoke "are you sure?" modals for these specific actions.
- Signed records lock from further edits — enforce this at the service layer, not just by hiding the edit button in the UI.

### 2.6 RBAC is enforced server-side, always
- Every controller method needs `@PreAuthorize` (or equivalent) matching the RBAC matrix in `CTMS_Requirements_Reference.md` Part A.4 / the Implementation Plan's RBAC table. A hidden UI button is not access control.
- Role names in code must match the canonical role list (Admin, Study Manager, Site Coordinator, Investigator, CRA/Monitor, Data Management, Finance Manager, QA/Compliance Officer/Auditor, Clinical Project/Program Leadership, Executive, Sponsor/CRO Leadership, Patient) — don't invent ad hoc role strings per feature.
- Document-level and field-level access rules (Epic 7 Story 05 — "patients can't see PI docs, CRAs can't see financials") are enforced in the service/query layer, not filtered client-side.

### 2.7 Configuration-driven, not hardcoded
Visit windows, document requirements, workflow steps, and payment triggers are **data**, not `if` statements, from Phase 1 onward — even before the Phase 10 no-code admin UI exists to edit them.
- New study-specific logic should mean a new row in a rules/config table, not a new code branch.
- If you catch yourself writing `if (studyId == X)` anywhere outside of a migration/seed script, stop — that's the exact hardcoding the BRD is designed to eliminate (§9.6 Configuration Risk exists precisely because of this).

---

## 3. Domain Language — Use Glossary Terms Exactly

Pulled from BRD §10 (`CTMS_Requirements_Reference.md` Part A.10). Don't substitute synonyms — inconsistent naming between code, DB, and domain docs is how traceability breaks during an audit.

| Use this term | Not this |
|---|---|
| `Study` | Trial, Protocol (Protocol is a separate concept — the document defining the study) |
| `Site` | Location, Center |
| `Subject` (entity/DB), `Patient` (portal-facing role/UI copy only) | Participant, User (for trial subjects) |
| `Visit` | Appointment, Encounter |
| `CRA` / Monitor | Reviewer |
| `MonitoringVisit` | Audit visit (reserve "audit" for compliance/QA audit trail context) |
| `Task` | Ticket, Action Item |
| `ESignature` | Approval (approval is the workflow step; e-signature is the specific mechanism) |
| `FPI` / `LPI` / `LPO` / `DBL` | Don't invent alternate abbreviations |

---

## 4. Backend Conventions (Java / Spring Boot)

- **Package by feature, not by layer**: `com.ctms.study`, `com.ctms.site`, `com.ctms.subject`, `com.ctms.visit`, `com.ctms.workflow`, `com.ctms.document`, `com.ctms.finance`, `com.ctms.config` (rules engine), `com.ctms.patientportal`, `com.ctms.compliance`, `com.ctms.common` (audit, notification, storage, security — Phase 0 shared services). Each feature package has its own `controller`, `service`, `repository`, `dto`, `entity` sub-packages.
- **Entities always include**: `id`, `createdBy`, `createdAt`, `modifiedBy`, `modifiedAt`, and a `status`/lifecycle enum where the backlog defines one. Don't omit audit columns "for now."
- **DTOs, not entities, cross the controller boundary.** Never return a JPA entity directly from a `@RestController` — map to a response DTO so we control exactly what's exposed (this is also how role-based field-hiding gets implemented per Epic 1 Story 04, Epic 3 Story 04, etc.).
- **Validation**: Jakarta Bean Validation annotations on DTOs for structural checks (required fields, format); custom validators for cross-field business rules explicitly named in the backlog (expiry > mfg date equivalents like "Visit 2 cannot occur before Visit 1," "protocol ID uniqueness," "eligibility criteria before enrollment").
- **State machines**: lifecycle transitions (Study Draft→Active→Conduct→Closeout; Subject Screened→Enrolled→In Treatment→Completed/Withdrawn; AE Open→Under Review→Resolved) are implemented as explicit, testable state machines with a guarded transition method — not a raw enum setter. Invalid transitions must throw, not silently succeed.
- **Migrations (Flyway)**: append-only. Never edit a migration that's already been applied/merged — write a new one. Name migrations descriptively (`V12__add_adverse_event_severity_column.sql`, not `V12__update.sql`).
- **Rules engine**: business rules that the backlog says must be "configurable without code" (visit templates, document-phase requirements, workflow steps, payment triggers) go through the Drools/JSON-rule-interpreter layer from Phase 0 — don't bypass it with a quick hardcoded conditional "just for now," because "for now" is how §9.6 Configuration Risk becomes real.
- **Testing**: given BRD §7.6 ("deterministic behavior, no AI ambiguity... easy to test workflows and configurations"), business-rule and state-machine logic needs thorough unit tests — these are the artifacts that make the system's "validation-friendly" positioning actually true, not just marketing copy from the Problem Statement. Use Testcontainers (Postgres) for repository/integration tests rather than mocking the DB layer for anything involving constraints (uniqueness, cascade behavior).

---

## 5. Frontend Conventions (Angular + Tailwind + Angular Material)

- **Standalone components**, Reactive Forms, typed forms where practical.
- **Styling split**: Tailwind handles layout, spacing, and page structure. Angular Material handles complex interactive widgets (data tables with sort/filter/pagination, date pickers, dialogs, steppers for lifecycle transitions, snackbars). Don't reach for a third UI kit or hand-roll a component Material already provides well.
- **One theme**: define a single Angular Material theme whose palette matches the Tailwind design tokens (colors, spacing scale) so the two systems don't visually clash. Don't let individual features pick their own ad hoc colors.
- **State**: RxJS services / Angular Signals by default. Don't introduce NgRx unless Phase 10 (System Configuration) genuinely proves the state complexity needs it — this was an explicit "don't reach for it preemptively" decision in the Implementation Plan.
- **No MFA challenge screens, no OTP input components** — see §2.2 above; don't scaffold UI for a flow that doesn't exist.
- **No `localStorage`/`sessionStorage` for anything security-sensitive** (tokens go through the auth interceptor's in-memory handling + refresh flow).
- **Role-based rendering** is a UX convenience, not a security boundary — every view that hides a field/action because of role must have that same restriction enforced server-side (§2.6). Don't treat `*appHasRole` as sufficient on its own.

---

## 6. API Conventions

- REST, resource-oriented (`/api/studies/{id}/sites`, `/api/subjects/{id}/visits`), not RPC-style action endpoints except for genuine actions without a clean resource shape (e.g., `/api/documents/{id}/approve`, `/api/payments/{id}/hold`).
- Pagination on every list endpoint (subjects, sites, documents, audit logs, tasks) — the BRD's scalability NFR (§7.2) explicitly expects large multi-study/multi-site datasets.
- Consistent error shape across the API (status, message, field-level validation errors) — don't let each controller invent its own error format.
- Every mutating endpoint's `@PreAuthorize` should be visibly traceable to the RBAC matrix — if you can't point to which role(s) should hit an endpoint, that's a sign the story wasn't fully read.

---

## 7. Definition of Done (per story)

Before considering a backlog story complete, confirm:
- [ ] Acceptance criteria from `CTMS_Requirements_Reference.md` are met literally, not just "close enough."
- [ ] Audit logging is wired for every create/update/delete/transition in scope.
- [ ] RBAC (`@PreAuthorize` + matching UI guard) matches the role(s) named in the user story ("As a Finance Manager...", "As a QA Auditor...").
- [ ] No hard deletes introduced; versioning/soft-delete pattern used where the story implies history matters.
- [ ] No hardcoded per-study/per-site logic where the story or a related Epic 9 (System Configuration) story implies it should be configurable.
- [ ] Unit tests cover the state-machine/business-rule branches, not just the happy path.
- [ ] No MFA/OTP/external API dependency introduced.
- [ ] Terminology matches §3 above (Study/Site/Subject/Visit, etc.).

---

## 8. Commands

*(Fill in once the repo is scaffolded — placeholders reflect the intended stack.)*

```bash
# Backend
./mvnw spring-boot:run          # run locally
./mvnw test                     # unit + integration tests (Testcontainers)
./mvnw flyway:migrate           # apply migrations

# Frontend
npm install
ng serve                        # dev server
ng test                         # unit tests
ng lint                         # lint
ng build --configuration production
```

---

## 9. When In Doubt

Re-read the relevant story in `CTMS_Requirements_Reference.md` before guessing. If the requirement is genuinely ambiguous or contradicts one of the rules in §2, say so explicitly rather than picking a silent interpretation — this is a compliance-sensitive system where an undocumented assumption is itself a finding waiting to happen.