# Phase 9 — Financial Management: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 8 (Stories 01–04): rule-based payment generation, budget planned-vs-actual tracking, budget version control, and payment holds/releases.

---

## 1. Context

Epic 8's BRD text (`CTMS_Requirements_Reference.md` lines 943–997) follows the same thin pattern as every prior phase (no RBAC matrix, no numeric thresholds, no cost-category enumeration) — but unusually, CLAUDE.md is directly prescriptive about this exact phase rather than silent: it explicitly names payments/budgets in §2.1 (no AI/ML for payment calculations), §2.4 (budgets follow Document's version-and-archive pattern, verbatim), §2.6/§6 (payment hold/release as its own literal example endpoint, `/api/payments/{id}/hold`), and §2.7 (payment triggers must be data-driven through the Drools layer). Four decisions were resolved with you directly before implementation:

1. **Budget/Payment relationship — linked.** `Budget` holds versioned per-cost-category planned amounts; "actual" is computed live by aggregating generated `Payment` records — no duplicate manual entry.
2. **Payment triggers — Visit + Site + Milestone.** `VISIT_COMPLETED` (new hook), `SITE_ACTIVATED` (extended existing hook), `MILESTONE_REACHED_FPI`/`MILESTONE_REACHED_LPI` (new hook, tied to Phase 8's real `Milestone` entity, not a raw subject count).
3. **RBAC — Finance Manager + Admin only**, for both read and write. The first phase with genuinely restricted read access (even Study Manager, who has broad read everywhere else, is blocked here) — matching CLAUDE.md's own "CRAs can't see financials" precedent.
4. **Payment release requires e-signature** (password + reason), reusing Phase 0's `ESignatureService` exactly like Study closeout and Document final-approval.

## 2. What Was Built

### Backend

- **Schema** (`V12__financial_management.sql`): `budget`, `budget_version` (`CURRENT`/`SUPERSEDED`, unique per study+type via `budget`'s own unique study constraint), `budget_line_item`, `payment` tables, plus a new `PAYMENT_RULES_DEFAULT` Drools rule set (Phase 0's previously-unused `CATEGORY_PAYMENT`) with 4 seed rules (visit completion, site activation, FPI, LPI).
- **`com.ctms.ctms_backend.payment`**: `PaymentRuleService` (mirrors `TaskRuleService`, but returns `Optional` since "no rule for this event" is a normal outcome, e.g. LPO/DBL milestones), `PaymentService` (`generatePayment` computes `amount = min(base × multiplier, cap)` in Java from Drools-supplied components — kept as separate fields on `Payment` so the calculation stays auditable, not just an opaque final number — plus `hold`/`release`, the latter requiring `ESignatureService.sign(...)`).
- **`com.ctms.ctms_backend.budget`**: `Budget`/`BudgetVersion`/`BudgetLineItem` following `DocumentVersion`'s version-and-archive pattern per CLAUDE.md's direct instruction; `BudgetService` (duplicate-budget rejection, mandatory reason for version 2+, actual/variance computed by joining `PaymentRepository`'s aggregation query against the current version's line items); `BudgetExportService` (PDF via `openpdf`, Excel via `poi-ooxml` — both previously-unused dependencies from Phase 0/8).
- **Hook-site edits**: `VisitService.markCompleted` (new `actorUsername` parameter threaded through from the controller, since this endpoint previously didn't need one), `SiteActivationService.promote` (payment generation added unconditionally, separate from the existing conditional CRA-assignment task), `MilestoneService.recordActual` (payment generation gated to `FPI`/`LPI` only).
- **RBAC**: every `/api/budgets/*` and `/api/payments/*` endpoint is `FINANCE_MANAGER`/`ADMIN` only — no broad-read fallback, a deliberate first for this project.
- **6 new exceptions** wired into the existing single `GlobalExceptionHandler`.

### Frontend

- `core/budgets/budget.service.ts`, `core/payments/payment.service.ts` (new).
- `features/finance/payment-list/`: filterable/paginated payment list, inline Hold (reason dialog) and Release (password + reason e-signature dialog, mirroring Document final-approval's exact 401-handling UX) actions.
- `features/finance/budget-detail/`: planned/actual/variance table for the current version, version history with a checkbox-based compare-any-two view (mirrors Document's existing version-comparison pattern), a "Create New Version" form requiring a reason, and PDF/Excel export buttons.
- Routes (`/payments`, `/studies/:studyId/budget`) and the shell nav "Payments" link are all gated to `FINANCE_MANAGER`/`ADMIN` — consistent with the restricted-RBAC decision, unlike broader-but-still-gated precedents like the AE board.

## 3. Defects Found & Fixed

No functional defects were found during implementation or your browser walkthrough — all backend logic, RBAC boundaries, and the full budget/payment lifecycle worked correctly on the first pass.

## 4. Known Gaps / Carried-Forward Items

- **Budget PDF export layout needs polish.** You confirmed the PDF export downloads correctly and contains the right data, but the visual structure/formatting is poor (likely table sizing/spacing in the `openpdf` `PdfPTable` layout in `BudgetExportService`). Flagged here for a follow-up pass — not blocking, since the underlying data and Excel export are both correct.
- **High-risk site thresholds / dashboard scoping gaps from Phase 8 remain unchanged** — this phase didn't touch dashboard code.
- **No per-study configurability of payment rule amounts** — `PAYMENT_RULES_DEFAULT`'s base amounts/multipliers/caps are fixed in the seeded DRL, consistent with how Phase 6/7/8 rule sets work today. If a future phase needs per-study payment amounts, that's a new rule-versioning migration (same pattern used to add `ADVERSE_EVENT_HIGH_SEVERITY` in Phase 7), not a new mechanism.
- **No re-hold after release** — `Payment.status` is strictly linear (`PENDING → ON_HOLD → RELEASED`); the BRD doesn't describe reopening a released payment, so this wasn't built. Revisit if a real workflow need surfaces.

## 5. Verification

- **Unit tests**: `PaymentRuleServiceTest` (3 cases — real event-code lookups via mocked `RuleSetService`, unrecognized code returns empty), `PaymentServiceTest` (8 cases — uncapped/capped amount calculation, hold guard, release guard, wrong-password rejection leaving the payment untouched), `BudgetServiceTest` (5 cases — duplicate rejection, missing-reason rejection, version increment/supersede, actual/variance aggregation).
- **Integration tests** (`FinancialManagementIntegrationTest`, `@SpringBootTest @Transactional` against the dedicated `ctms_testdb`, 2 cases): full budget v1 → real visit completion → real Drools-generated payment → hold → wrong-password release rejected → correct-password release with a real `ESignature` row → budget v2 with reason → v1 confirmed `SUPERSEDED` and read-only → actual/variance confirmed correct; separate case confirming site activation generates a real `SITE_ACTIVATED` payment via Drools. Full backend suite: **164/164 passing** (up from 146 pre-Phase-9).
- **Manual `curl` pass** against real Postgres: budget create/duplicate-rejected/version-2-without-reason-rejected/version-2-with-reason; visit completion and site activation both auto-generating correctly-shaped payments; hold/release with wrong-then-correct password; actual/variance and version-history/comparison all correct; LPO milestone confirmed to generate no payment; PDF/Excel export both producing valid files; every non-Finance/Admin role (including Study Manager, who is broad-read everywhere else) blocked with 403 from both `/api/budgets/*` and `/api/payments/*`.
- **Browser walkthrough** with you: payments list, hold/release with e-signature, budget versioning, version comparison, and RBAC (nav link visibility + direct-URL guard redirects) all confirmed working. The PDF export layout issue (§4) was the only issue raised, and is cosmetic, not functional.
- **Frontend**: `ng build --configuration production` and `ng test` both pass.

## 6. Ready for Next Phase

Financial Management is fully functional and independently verified, reusing Phase 0's Drools engine, Phase 0's e-signature primitive, Phase 2's version-and-archive pattern, and Phase 8's export libraries without modifying any of them — consistent with this project's pattern of extending shared infrastructure rather than duplicating it per feature.
