# Phase 10 — System Configuration: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 9 (Stories 01–04): no-code workflow rule editing, visit template dependency rules, document-requirement-by-phase mapping, and a unified rule editor across the system.

---

## 1. Context

Epic 9's BRD text (`CTMS_Requirements_Reference.md` lines 1001–1060) follows this project's now-familiar pattern: BRD language is thin on structural detail — no workflow step/transition schema, no visit-dependency data model, no document-rule schema — and "Admin" is the only actor named across all four stories. Per the implementation plan doc's own framing, this phase "builds the editing UI on top of the rules-engine groundwork laid in Phase 0" — `RuleSet`/`RuleDefinition`/`RuleSetService`/`DroolsRuleEngine` have existed since Phase 0 and been silently consumed by Phase 6 (Task), Phase 4 (Eligibility), and Phase 9 (Payment), but there was never a UI to view or edit rules — only raw REST calls each prior phase used once, by hand, in a migration.

The research for this phase surfaced a real, worth-flagging gap: **CLAUDE.md §2.7 claims visit windows and document requirements are already "data, not `if` statements... even before the Phase 10 no-code admin UI exists."** That's only true for workflow steps and payment triggers. `RuleSet.CATEGORY_VISIT` and `CATEGORY_DOCUMENT` existed as constants but had zero seeded rule sets before this phase; visit windows were plain `VisitTemplate` columns, and document requirements didn't exist as any data model at all.

Four architectural decisions were resolved with you directly before implementation:

1. **Workflow editor (Story 01) — satisfied by the unified rule editor (Story 04), no new step-graph model.** No workflow step/transition entity exists anywhere in the codebase; `TASK_RULES_DEFAULT` is flat event→outcome, not a graph. Story 01 ships as "Admins edit `WORKFLOW`-category rule sets through the same editor as everything else" rather than a bespoke drag-and-drop step builder.
2. **Rule editor fidelity — DRL textarea + compile-validate, not a DRL-free visual builder.** Admin edits DRL directly in a textarea; saving reuses `RuleSetService.addDefinition`, which already compiles before persisting (bad DRL never activates).
3. **Visit dependencies — simple FK + real blocking guard.** A nullable `dependsOnVisitTemplate` self-referential FK on `VisitTemplate`, enforced in `VisitService.markCompleted`.
4. **Document requirements — gate Study lifecycle transitions**, not Site activation, matching the BRD's literal "Start-Up/Conduct/Closeout" phase wording.

### Intentional deviation from CLAUDE.md's literal wording (documented, not silent)

Visit-dependency and document-requirement checks are **not** routed through Drools, despite `CATEGORY_VISIT`/`CATEGORY_DOCUMENT` existing as unused constants. Both are fundamentally "does a row exist" relational constraints (a self-FK, a per-study mapping table), not trigger-event-driven decisions like Task/Payment — expressing them as Drools facts/rules would have been over-engineering for what a plain guarded entity relationship already does deterministically and auditably. Drools stays reserved for `WORKFLOW`/`PAYMENT`/`ELIGIBILITY`, where evaluate-against-facts genuinely fits. This resolves the CLAUDE.md §2.7 gap, just not via the exact mechanism its wording implied — a reasoned, intentional deviation, not an oversight.

Also intentionally out of scope this phase: Phase 8's high-risk-site thresholds and Phase 9's payment-rule amounts were both flagged in their own phase reports as "revisit if needed," not committed Epic 9 deliverables — folding them in would have been scope creep on an already-substantial phase.

## 2. What Was Built

### Backend

- **Schema** (`V13__system_configuration.sql`): `visit_template.depends_on_visit_template_id` (nullable self-FK), new `document_requirement` table (per-study mandatory-category-by-phase mapping, unique on study+phase+category). No new `RuleSet`/`RuleDefinition` seed rows — the rule editor operates on the 3 already-seeded rule sets from Phases 4/6/9.
- **Rule editor backend**: `RuleSetService.list()`/`getDetail()` (new read methods, backed by `RuleSetRepository.findAll()`/`findByName()` which already existed) and two new `GET` endpoints on `RuleSetController` — no changes to the existing create/add-version/evaluate endpoints, which the new UI reuses directly.
- **Visit dependencies**: `VisitTemplateService.create`/`update` validate a `dependsOnVisitTemplateId` against same-study membership (`CrossStudyDependencyException`) and cycle-freedom (`VisitTemplateDependencyCycleException`, walks the chain). `VisitService.markCompleted` blocks completion (`VisitDependencyNotMetException`) unless the subject's sibling visit for the prerequisite template is already `COMPLETED` — including the case where the prerequisite visit doesn't even exist yet.
- **Document requirements**: `com.ctms.ctms_backend.document.service.DocumentRequirementService` (`create`, `listByStudy`, `checkRequirementsMet`) and `DocumentRequirementController` (Admin-only writes, broad non-patient reads). `StudyService.transition` and `StudyService.closeout` both call `checkRequirementsMet` before allowing `DRAFT→ACTIVE`, `ACTIVE→CONDUCT`, or `CONDUCT→CLOSEOUT`, throwing `MissingMandatoryDocumentsException` (lists the missing categories) if any mandatory category for the target phase has no `CURRENT` document linked to the study.
- **RBAC**: rule-editor endpoints stay Admin-only (matching `RuleSetController`'s existing precedent). `DocumentRequirement` writes are Admin-only; reads are broad (all non-patient operational roles) — a deliberate contrast with Phase 9's restricted-read financial data, since knowing what's required is ordinary operational information.

### Frontend

- `core/rule-sets/rule-set.service.ts`, `core/document-requirements/document-requirement.service.ts` (new).
- `features/admin/rule-sets/rule-set-list/` + `rule-set-detail/`: fills the dead `/admin/rule-sets` nav link that was planted back in an earlier phase specifically for this one. List page groups rule sets by category with latest-version numbers; detail page shows full version history and a DRL textarea pre-filled with the active version, surfacing compile errors inline on save failure.
- `features/document-requirements/`: per-study list + Admin-only create form, added as a new "Document Requirements" tab on the study detail page alongside Milestones/Budget.
- `features/visit-templates/`: added a "Depends On" dropdown (same-study templates only) to the create/edit form, and dependency info shown in the template list.
- Routes and nav wired per the RBAC split above.

## 3. Defects Found & Fixed

No functional defects were found during implementation or your browser walkthrough. All backend logic, RBAC boundaries, and the full dependency/requirement lifecycle worked correctly on the first pass.

## 4. Feedback From Walkthrough

You confirmed all four walkthrough items work as intended (rule editor, visit dependencies, document requirements, RBAC). The one piece of feedback was that the DRL rule syntax itself wasn't self-explanatory from the UI alone — I walked through the structure of a DRL rule (package/import/global boilerplate, `when`/`then` condition-action pairs, which parts are safe to edit vs. tied to Java class shapes) directly in conversation. This is worth calling out as a **known UX gap**: the rule editor exposes raw DRL text with no in-app explanation of its structure, which is exactly the trade-off flagged in design decision #2 above (DRL textarea, not a DRL-free visual builder) — Admins will need either this kind of one-time walkthrough or in-app documentation before editing rules confidently. A follow-up (in-app help text, or example/template rules pre-filled per category) would close this gap if it proves to be a recurring friction point.

## 5. Verification

- **Unit tests**: `RuleSetServiceTest` (new — 3 cases: list, detail, unknown-name), `VisitTemplateServiceTest` (extended — 3 new cases: same-study dependency succeeds, cross-study rejected, cycle rejected), `VisitServiceTest` (extended — 3 new cases: prerequisite incomplete/missing/completed), `DocumentRequirementServiceTest` (new — 5 cases: create, all-satisfied, missing-mandatory, non-mandatory-ignored, mixed), `StudyServiceTest` (extended — 2 new cases: transition and closeout both blocked by missing mandatory documents).
- **Integration tests** (`SystemConfigurationIntegrationTest`, `@SpringBootTest @Transactional` against `ctms_testdb`, 4 cases): real DRL add-version with valid content (activates) and invalid content (`RuleCompilationException`, active version unchanged); two real visit templates with a dependency — out-of-order completion blocked, in-order completion succeeds; cross-study dependency rejected against two real studies; real document requirement blocking a real Study `DRAFT→ACTIVE` transition until a real `CURRENT` document is uploaded. Full backend suite: **184/184 passing** (up from 164 pre-Phase-10).
- **Manual `curl` pass** against real Postgres (dev DB, restarted to pick up the new code and confirmed schema via `psql \d`): rule-set list/detail, bad-DRL rejection (400, unchanged active version) and good-DRL activation; visit-template dependency creation, out-of-order completion blocked then succeeding in order; document requirement creation, RBAC-blocked write for Study Manager, broad read confirmed; Study transition blocked with missing categories listed, then succeeding after upload; Admin/Study-Manager/Patient RBAC all confirmed across both new surfaces, including Patient correctly blocked from document-requirement reads (a deliberate exclusion, unlike Document's own broader read list).
- **Browser walkthrough** with you: rule editor (edit, break, fix, version history), visit dependency out-of-order blocking, document-requirement-gated study transition, and RBAC (nav visibility + direct-route/API blocking for Study Manager and Patient) all confirmed working.
- **Frontend**: `ng build --configuration production` and `ng test` both pass; new lazy chunks (`rule-set-list-component`, `rule-set-detail-component`, `document-requirements-component`) confirmed present in the build output.

## 6. Known Gaps / Carried-Forward Items

- **DRL rule editor lacks in-app guidance** (see §4) — Admins need to already understand DRL structure or receive a walkthrough; no in-app help text or example templates exist yet.
- **Workflow editor (Story 01) is satisfied only at the "edit the underlying rule set" level**, not a literal visual step/transition graph with drag-and-drop reordering — an explicit, confirmed design trade-off (§1, decision #2), not an oversight.
- **Visit/Document rule categories (`CATEGORY_VISIT`, `CATEGORY_DOCUMENT`) remain unseeded in Drools** — intentional, since both are implemented as plain guarded entity relationships instead (§1, deviation note). `CATEGORY_NOTIFICATION` also remains unused, unrelated to this phase.
- **Phase 8's high-risk-site thresholds and Phase 9's payment-rule amounts remain hardcoded** — explicitly out of scope this phase, revisit if a real per-study configurability need surfaces; the rule editor built this phase makes that migration straightforward whenever it's needed.

## 7. Ready for Next Phase

System Configuration is fully functional and independently verified, reusing Phase 0's Drools infrastructure, Phase 1's Study lifecycle guard pattern, and Phase 5's VisitTemplate/VisitService without duplicating any of them — consistent with this project's pattern of extending shared infrastructure rather than building parallel mechanisms per feature.
