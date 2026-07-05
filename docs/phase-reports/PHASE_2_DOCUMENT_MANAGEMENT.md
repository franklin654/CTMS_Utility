# Phase 2 — Document Management & Compliance: Completion Report

**Status:** Complete
**Scope:** Backlog Epic 7 (Stories 01–05): document upload/versioning, two-stage reviewer→approver approval workflow with e-signature, full audit trail, category-based role-restricted access.

---

## 1. What Was Built

### Backend

- **Schema** (`V4__document_approval_workflow.sql`): added `document.study_id` (nullable FK to `study`), `document_review` (append-only reviewer/approver action log, optional FK to `e_signature`), `document_category_access_rule` (data-driven category→role DENY list), `document_workflow_role` (data-driven reviewer/approver role per category, `NULL` category = default).
- **State machine**: `DocumentVersionStatus` enum (`DRAFT → PENDING_REVIEW → PENDING_APPROVAL → CURRENT`, with `REJECTED` as a terminal branch off either pending state), enforced in `DocumentWorkflowService` via a `Map<DocumentVersionStatus, Set<DocumentVersionStatus>>` guard, mirroring `StudyService`'s pattern. `REJECTED` is terminal — a rejected version is never resurrected; the uploader must create a new version (ALCOA immutability).
- **Two-stage workflow**: `submitForReview` (`DRAFT→PENDING_REVIEW`), `reviewerDecide` (approve→`PENDING_APPROVAL`, reject/request-changes require a mandatory comment), `approverFinalDecide` (approval calls `ESignatureService.sign(...)` — password re-auth + reason — before promoting to `CURRENT` and archiving the prior current version; rejection requires a comment, no e-signature, since only the positive approval is the Part 11 "sign-off" action).
- **Role-gating is data-driven, not hardcoded**: `document_workflow_role` and `document_category_access_rule` tables back the fine-grained checks, layered underneath coarser static `@PreAuthorize` role sets on the controllers — per CLAUDE.md §2.7.
- **Category-based access control** (`DocumentAccessControlService`): default-allow / explicit-deny-list — a caller whose role is `DENY`-listed for a document's category gets a 403 **and** an `ACCESS_DENIED` audit row is written, satisfying the "unauthorized access attempts blocked and logged" requirement. Enforced in `DocumentService.get()`, `downloadVersion()`, and at the DB level in `list()` (a `NOT IN` subquery, not a post-fetch filter, so pagination counts stay correct).
- **RBAC gap fix**: Phase 0's `DocumentController` had zero `@PreAuthorize` annotations — fixed with `WRITE_ROLES` (`STUDY_MANAGER`, `SITE_COORDINATOR`, `ADMIN`) and `READ_ROLES` (all internal roles) constants, matching `StudyController`'s established pattern.
- **New `DocumentApprovalController`** for the workflow-specific endpoints (`/submit`, `/review`, `/approve`, `/reviews`, `/approval-queue`), kept separate from the flat `DocumentController` so the two RBAC surfaces don't tangle.
- **5 new exceptions** wired into the existing single `GlobalExceptionHandler`.

### Frontend (`features/documents/`, `features/admin/audit-log/`)

- `document-list` (paginated table, role-gated upload button), `document-upload` (title/category/study/file form).
- `document-detail`: version history table with inline lifecycle actions (submit/review/approve/reject) gated per-row by status and role, a metadata-only version comparison (checkbox-driven, side-by-side table — not a content diff, which isn't feasible for arbitrary binaries), and inline "Add Version".
- `document-approval-queue`: a cross-document Review/Approval tab view for reviewers/approvers who don't want to hunt through individual documents.
- `document-review-dialog` (plain comment, mandatory unless approving) and `document-approve-dialog` (password + reason e-signature, mirrors Phase 1's `study-closeout-dialog` structure exactly) — both reused across `document-detail` and `document-approval-queue`.
- **Audit Log viewer** (`features/admin/audit-log/`) — this nav link existed since Phase 0 but had no matching route; now wired with entity-name/entity-id filtering, pagination, and CSV export.
- Routes added under `ShellComponent`, same lazy `loadComponent` pattern as Phase 1; nav links added for "Documents" and "Approval Queue".

## 2. Defects Found & Fixed

1. **Field-initializer-ordering bug recurred in `document-review-dialog`** — `readonly commentRequired = this.data.action !== 'APPROVED'` was evaluated before the constructor's `@Inject(MAT_DIALOG_DATA)` parameter assignment completed, the same class of bug fixed in Phase 0/1. Fixed by moving the derived fields into the constructor body instead of class-field initializers.
2. **PDF uploads failing with a raw 500.** Real PDFs exceeded Spring's undocumented default multipart limit (1MB/file) — invisible during backend curl testing because those tests used tiny text fixtures. Caught during your browser walkthrough with a real PDF. Fixed by setting `spring.servlet.multipart.max-file-size`/`max-request-size` to 25MB (configurable via `UPLOAD_MAX_FILE_SIZE`/`UPLOAD_MAX_REQUEST_SIZE` env vars), and added a dedicated `MaxUploadSizeExceededException` handler returning a clean 413 with a clear message instead of falling through to the generic 500 handler. Verified with a real 2MB PDF (including spaces in the filename, which was not actually the cause) and a deliberately-oversized 30MB file.

## 3. Verification

- **Unit tests** (16 total): `DocumentWorkflowServiceTest` (12 cases, `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`) covers every state-machine branch, role-mismatch, mandatory-comment rule, and e-signature success/failure; `DocumentAccessControlServiceTest` (4 cases) covers allow/deny per category with `SecurityContextHolder` set directly.
- **Integration tests** (3 cases, `DocumentApprovalWorkflowIntegrationTest`, `@SpringBootTest @Transactional` against the dedicated `ctms_testdb`): full lifecycle, rejection path, category-deny-with-audit path.
- **Manual `curl` sequence** against real Postgres with 4 real bcrypt-hashed users across different roles: upload→`CURRENT`, addVersion→`DRAFT`, submit→`PENDING_REVIEW`, reviewer-approve→`PENDING_APPROVAL`, wrong-role/wrong-password rejections, correct final-approve→`CURRENT`+old-version-`ARCHIVED`+signed, reject-without-comment→400, reject-with-comment→`REJECTED`, category-based 403s with audit rows, CSV export, list-level DB filtering.
- **Browser walkthrough** with you: full document lifecycle (upload → add version → submit → review-approve → final-approve with password dialog → old version archived), rejection path with mandatory comment, version comparison checkboxes, Audit Log filter + CSV export. The PDF upload-size bug (item 2 above) was only caught this way, since automated tests used tiny fixtures.

## 4. Known Gaps / Carried-Forward Items

- Version comparison is metadata-only (filename, uploader, effective date, size, checksum, status) — no content-level diff, since arbitrary binary/PDF diffing isn't in scope for this phase.
- Document category taxonomy (`PROTOCOL`, `INFORMED_CONSENT`, `PRINCIPAL_INVESTIGATOR_CV`, `REGULATORY_APPROVAL`, `FINANCIAL`, `MONITORING_REPORT`, `SOP`, `OTHER`) is a free-text column, not a DB enum — matches the BRD's only two literal examples ("PI documentation," "financial files"); more categories can be added later without a migration.
- `document.study_id` remains nullable — some documents may be platform-level, not tied to a specific study; tighten later if the product decision solidifies.
- Same Docker/Testcontainers gap as Phases 0/1 — integration tests use the dedicated `ctms_testdb` Postgres database instead.

## 5. Ready for Phase 3
