# Phase 12 — Regulatory Compliance Hardening: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 11 (Stories 01–04): informed-consent gating (GCP compliance enforcement), e-signature expansion (21 CFR Part 11 hardening), protocol-deviation logging (ALCOA data integrity), and a consolidated end-to-end traceability report.

---

## 1. Context

Research confirmed upfront that this phase is **overwhelmingly new-feature-shaped, not bug-fixing-shaped**: every entity across all 11 prior phases was already soft-delete-only (`CLAUDE.md` §2.4), every state-changing service call already audited (§2.3), and e-signature already existed as a reusable primitive (§2.5) with 3 real call sites (Study closeout, Document final-approval, Payment release). "Hardening" undersold it — the real scope was closing three specific gaps that genuinely didn't exist yet (informed-consent tracking, protocol-deviation logging, cross-entity traceability), plus a deliberate decision on how far to expand e-signature coverage.

Four decisions were confirmed with you before implementation:

1. **Consent tracking — document-category gate**, not a dedicated `ConsentStatus` entity. Reuses `Document.category = "INFORMED_CONSENT"` plus a blocking check, mirroring Phase 10's `DocumentRequirementService` pattern but at per-subject granularity.
2. **Protocol deviations — simple log-only entity**, no `AdverseEvent`-style review workflow. Just a permanent "this happened" record.
3. **E-signature expansion — added to all three identified gaps**: Subject withdrawal, Adverse Event resolution, Site activation. All three are irreversible or safety/compliance-critical transitions, the same category as the 3 actions that already required it.
4. **Reason-code scope — kept as-is** (compliance-sensitive state transitions only, not every ordinary field edit) — a full literal reading of BRD Story 03 AC2 would have added a mandatory reason prompt to nearly every minor edit across the app, out of proportion to the BRD's own thin wording.

A real schema gap surfaced during planning: `Document` had no `subject` FK, only a nullable `study` FK. A study-scoped consent check would have incorrectly cleared the gate for every subject in a study once any one subject's consent was uploaded — fixed by adding a new nullable `Document.subject` FK and scoping the check by `subject.id`. A related ordering question (a Subject must exist before a subject-linked Document can reference it, so the gate can't block enrollment itself) was resolved by gating **visit completion** instead: scheduling a visit is calendar bookkeeping, but completing it is genuine subject activity — a documented, deliberate scope decision rather than a silent assumption.

## 2. What Was Built

### Backend

- **Migration `V15__regulatory_compliance_hardening.sql`**: `document.subject_id` FK; `esignature_id` FK added to `subject_status_history`, `adverse_event`, and `site`; new `protocol_deviation` table (`subject_id NOT NULL, description, severity, deviation_date`, standard audit columns, no status/workflow columns).
- **`ConsentGateService`** (`document/service`): `assertConsentPresent(Subject)` throws `MissingConsentException` (400) if no `CURRENT` `INFORMED_CONSENT` document exists for that specific subject. `VisitService.markCompleted` now calls it alongside the existing Phase 10 dependency guard.
- **New `com.ctms.ctms_backend.deviation` package**: `ProtocolDeviation` entity (`subject`, `description`, `severity` enum `MINOR/MAJOR/CRITICAL`, `deviationDate`), `ProtocolDeviationService.report/list`, `ProtocolDeviationController` (`POST /api/protocol-deviations`, `GET /api/protocol-deviations?subjectId=`), RBAC mirroring `AdverseEventController` exactly.
- **E-signature expansion (3 call sites, same pattern each time)**: `SubjectLifecycleService.withdraw`, `AdverseEventService.resolve`, and `SiteActivationService.attemptActivation` (which previously took no request body at all — now requires `password` + `reason`) each call `ESignatureService.sign(...)` before completing the transition and attach the resulting `ESignature` to the entity. Wrong password → `InvalidCredentialsException` (401), record stays completely untouched — identical behavior to the 3 pre-existing call sites.
- **`AuditLogController`**: CSV export now includes `beforeValue`/`afterValue` columns (previously present on the entity/DTO but silently omitted from the export). New `GET /api/audit-logs/traceability/{entityName}/{entityId}` consolidates that entity's full audit trail with any e-signatures captured against it, reusing `ESignatureService.history` unchanged — no new entity or duplicated query logic.
- **`DocumentService.createDocument`/`DocumentController.upload`**: gained an optional `subjectId` parameter — a necessary, non-optional extension, since without it there was no way for staff to actually attach a consent document to a subject through any existing endpoint.

### Frontend

- **`subject-detail.component`**: new "Protocol Deviations" card (report form + list) mirroring the existing Adverse Events section exactly, backed by a new `core/protocol-deviations/protocol-deviation.service.ts`. Withdraw and AE-resolve forms each gained a password field alongside their existing reason/notes fields.
- **`SiteActivationDialogComponent`** (new, `features/sites/site-activation-dialog`): password + reason e-signature dialog, mirroring `payment-release-dialog` exactly, wired into `site-detail.component`'s "Attempt Activation" button via `MatDialog`.
- **`features/admin/audit-log`**: new "View Traceability" button (enabled once both entity name and ID are filled in) rendering a consolidated panel of e-signatures and full audit trail below the existing paginated table.
- Wrong-password errors across all three e-signature forms use the established `err.status === 401 → "Incorrect password. Please try again."` convention already used by Document approval/Payment release.

## 3. A Real Design Gap Found (documented, not silently patched)

`SiteActivationService`'s auto-promotion path — completing a site's *last* checklist item via `updateChecklistItem` — still promotes the site to `ACTIVE` silently, without e-signature, exactly as it did before this phase. Only the *explicit* `attemptActivation` action was scoped for e-signature per your decision. In practice, completing checklist items one at a time (the normal staff workflow) means the silent path fires before the explicit one ever gets a chance to. This was surfaced by the integration test itself: proving the signed path actually works required directly completing checklist items via the repository (bypassing `updateChecklistItem`'s own auto-promotion check) to reconstruct a state — all items complete, still `PENDING_ACTIVATION` — that the normal API can't otherwise produce. The signed `attemptActivation` path is implemented correctly and works when reached; it just isn't the path most sites will take to go live today. Closing this properly would mean either removing the silent auto-promotion behavior (a bigger, not-yet-discussed change to established Phase 3 behavior) or requiring e-signature on every checklist-item completion (out of proportion to what that action represents). Flagging this rather than silently reworking Phase 3 behavior beyond what was scoped.

## 4. Verification

- **Unit tests** (all new/extended): `ConsentGateServiceTest` (3 — present/missing/per-subject-not-per-study isolation), `VisitServiceTest` (+2 — blocked/succeeds), `ProtocolDeviationServiceTest` (4), `SubjectLifecycleServiceTest` (+1 — wrong password leaves status untouched), `AdverseEventServiceTest` (+1), `SiteActivationServiceTest` (+1), `AuditLogControllerTest` (new — 2, export columns + traceability consolidation).
- **Integration tests** (`RegulatoryComplianceIntegrationTest`, `@SpringBootTest @Transactional` against `ctms_testdb`, 5 cases): consent gate blocked-then-unblocked with real per-subject isolation (two subjects in the same study, uploading one's consent never clears the other's gate); real protocol deviation creation + audit trail; all 3 e-signature-expanded actions — wrong password rejected leaving state untouched, correct password succeeds with a real `ESignature` row attached (site-activation case constructs the genuinely-reachable signed-promotion state directly via repository, per §3); traceability endpoint returns both `AuditLog` and `ESignature` rows for entities that have both. Full backend suite: **216/216 passing** (up from 197 pre-Phase-12).
- **Manual `curl` pass** against real Postgres (`ctms_db`, live dev server via Spring Boot DevTools auto-restart): consent gate blocked/unblocked with real per-subject isolation; protocol deviation report + list; all 3 e-signature expansions (wrong password → 401, correct password → success with a real signature row); traceability endpoint output; audit-log CSV export confirmed with `beforeValue`/`afterValue` columns present. All test data and demo accounts cleaned up afterward.
- **Browser walkthrough** with you: consent gate, protocol deviations, withdraw/AE-resolve/site-activation e-signature forms, and audit-log traceability all confirmed working — no defects found.
- **Frontend**: `ng build` and `ng test` both pass.

## 5. Known Gaps / Carried-Forward Items

- **Site activation's silent auto-promotion path bypasses e-signature** — see §3. Worth a deliberate decision in a future phase (or as a standalone fix) rather than carrying it forward indefinitely.
- **Reason-code scope intentionally stays narrow** (per decision #4) — ordinary field edits still don't require a reason code. If a future audit finding demands broader coverage, that's a new scope decision, not an oversight here.
- **No dedicated `ConsentStatus` entity** — consent is inferred entirely from `Document.category`. If a future story needs consent *versioning* semantics beyond "is there a CURRENT informed-consent document," that's a bigger addition than this phase's document-category gate.

## 6. Ready for Next Phase

Regulatory Compliance Hardening is fully functional and independently verified, closing the three genuine BRD gaps (consent tracking, protocol deviations, traceability reporting) and extending e-signature to all three identified irreversible/compliance-critical transitions — while reusing Phase 0's `AuditLog`/`ESignature` primitives, Phase 2's document infrastructure, and Phase 3's site-activation state machine without duplicating any of them.
