# Feasibility Assessment: No-Code Visual Rule Builder

**Status:** Assessment only -- no code in this deliverable. Written as part of Bug Fix Phase 10 (Rule Sets clarity), per the "Feature & Workflow Testing Report" comment that raw DRL text is not truly "no-code" for a non-developer admin.

## Current State

Rule sets (`ELIGIBILITY`, `WORKFLOW`, `PAYMENT` categories) are stored as versioned DRL (Drools Rule Language) text, edited via a plain textarea in `rule-set-detail.component`, and evaluated by the Drools engine at runtime (`RuleSetService`/`DroolsRuleEngine`, Phase 0). This satisfies CLAUDE.md #2.1's requirement that business logic be "deterministic" and "inspectable/explainable" -- every rule is real DRL text an engineer (or, with the Phase 10 documentation now added, a careful admin) can read top to bottom. It is not, however, genuinely no-code: writing or editing a rule still means writing Drools syntax correctly, including Java-typed `when`/`then` clauses and fact class field access.

## What "Genuinely No-Code" Would Require

A visual rule builder that lets a non-developer configure rules through structured UI (condition/action pickers, dropdowns, value inputs) instead of raw text, then compiles that structure into DRL (or an equivalent internal representation) behind the scenes. Concretely:

1. **A rule schema per category** -- for each of `ELIGIBILITY`/`WORKFLOW`/`PAYMENT`, a machine-readable description of: available fact fields, their types (string/enum/boolean/number), allowed operators per type, and the shape of the outcome the rule produces (a validation message, a `TaskRuleOutcome`, a `PaymentRuleOutcome`). This is the highest-leverage piece of new work -- everything else is built on top of it.
2. **A condition builder UI** -- structured "when" clause construction: field picker -> operator picker -> value input, with AND-only or AND/OR grouping (Drools rules in this codebase are currently single-condition; supporting multiple ANDed conditions per rule is a modest extension, OR-groups are a bigger one).
3. **An outcome/action builder UI** -- structured "then" clause construction, one form per category's outcome shape (e.g. Payment: cost category dropdown, base amount, multiplier, optional cap, currency).
4. **A DRL compiler** -- server-side (safer than client-side) code that takes the structured rule (from steps 2-3) and emits valid DRL text, then goes through the exact same `RuleSetService.addDefinition` versioning path that exists today. This is the piece that has to be airtight: a bug here writes bad DRL that either fails to compile (caught, safe) or compiles but does the wrong thing silently (not caught, unsafe) -- needs thorough testing per category before trusting it.
5. **A round-trip story for existing rules** -- every rule set currently in the database was authored as hand-written DRL (see the three seed examples now documented in the UI). A visual builder either needs to (a) parse existing DRL back into the structured schema so old rules remain editable in the new UI, which is a non-trivial DRL parser, or (b) accept that pre-existing rules stay text-only/read-only and only new rules use the builder, which is much simpler but creates two tiers of rules.
6. **A "preview/dry-run" step** -- given this is 21 CFR Part 11-adjacent business logic (eligibility gates, payment triggers), admins will want to see what a rule change would have done against recent historical events before activating it live. Not strictly required for v1, but likely requested immediately after v1 ships.

## Effort and Risk

This is a genuine net-new feature, not a bug fix or UX polish item, and not comparable in size to anything else in this bug-fix plan:

- **Schema + compiler for one category (e.g. PAYMENT, the simplest outcome shape)**: a multi-day effort on its own, including the test coverage this codebase's conventions require for anything touching payment generation.
- **All three categories** (each has a different outcome shape and different fact fields) roughly triples that, since the condition builder can likely be shared but the outcome builder cannot.
- **Backward-compat/parsing for existing rules**: open-ended risk -- DRL is a full rule language; parsing arbitrary existing DRL back into a constrained visual schema either requires restricting what "existing" rules are allowed to look like (a migration/cleanup pass) or accepting the two-tier approach above.
- **Ongoing risk**: a compiler bug that emits syntactically valid but semantically wrong DRL is the dangerous failure mode -- it wouldn't be caught by "the rule saved successfully," only by testing the specific compiled rule against real trigger events. This needs a deliberate test strategy, not just unit tests on the compiler in isolation.

## Recommendation

Do not fold this into the current bug-fix plan or the original 13-phase CTMS implementation plan -- it's out of scope for both. Treat it as a candidate for a dedicated future phase (call it a proposed "Phase 14: No-Code Rule Builder") with its own requirements-gathering step, since the biggest open question isn't technical feasibility (it's feasible) but product scope: which of the two rule-compatibility strategies in point 5 above is acceptable, and whether a dry-run/preview capability (point 6) is a v1 requirement or a fast-follow. Both are product decisions, not engineering ones, and should be settled before estimating a phase plan.

In the meantime, this bug-fix phase's documentation additions to `rule-set-detail.component` (plain-language explanation of what each category's rules do, the fact fields available, and a real example rule per category, sourced directly from the actual seeded DRL) meaningfully close the gap for the common case of an admin reading or lightly editing an existing rule, without committing to the larger no-code builder effort.
