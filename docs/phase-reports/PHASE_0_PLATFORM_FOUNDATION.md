# Phase 0 — Platform Foundation: Completion Report

**Status:** Complete
**Scope:** Cross-cutting services reused by every later phase — auth/RBAC, audit log, e-signature, notifications, document/file storage, rules engine skeleton, and the Angular auth shell.

---

## 1. What Was Built

### Backend (`ctms-backend`)

| Area | Delivered |
|---|---|
| **Datasource / migrations** | Flyway-managed schema (`V1__phase0_platform_foundation.sql`, `V2__password_reset_token.sql`) covering users/roles, audit log, e-signature, notifications, documents, rules engine tables. Hibernate runs in `validate`-only mode — Flyway owns the schema. |
| **Auth** | JWT access tokens (stateless, RBAC claims embedded) + opaque, hashed, rotating refresh tokens. Login, refresh, logout, change-password, forgot-password, reset-password endpoints. Account lockout after 5 failed attempts (30 min), password complexity + history policy, single-factor by design (org constraint — no MFA). First admin user bootstrapped from env vars since no user-management UI exists yet. |
| **RBAC** | 12 roles seeded per BRD §4. `@PreAuthorize` + method security enabled globally; JWT filter builds the security context directly from token claims (no per-request DB hit). |
| **Audit log** | Generic immutable `AuditLog` entity; `AuditService.record(...)` for precise before/after diffs (used by e-signature, documents, rule sets); `@Audited` + AOP aspect for simpler auto-logging; read/export REST endpoints restricted to ADMIN/QA_COMPLIANCE_AUDITOR. |
| **E-signature** | Password re-authentication + mandatory reason-for-signing, immutably recorded per entity, reusable across future phases (21 CFR Part 11 groundwork). |
| **Notifications** | In-app `Notification` entity + event-driven `NotificationService` (Spring `ApplicationEvent`), fanning out to email via `MailService`. Email is **disabled by default** (`notification.email.enabled=false`) — logs instead of sending until pointed at a real internal SMTP relay. List/unread-count/mark-read/mark-all-read endpoints. |
| **Document/file service** | `StorageService` abstraction (local disk now, S3-ready interface), SHA-256 checksums, versioned `Document`/`DocumentVersion` with auto-archive-on-new-version, upload/download/version-history endpoints. |
| **Rules engine skeleton** | `RuleSet`/`RuleDefinition` schema + Drools/KIE wiring (`DroolsRuleEngine`, `RuleSetService`). DRL is compile-validated before a definition can be activated — malformed rules never go live and return a structured 400 with the parser error. Versioned activation (old version auto-deactivated when a new one is added). |

### Frontend (`ctms-frontend`)

| Area | Delivered |
|---|---|
| **Auth core** | `AuthService` (login/refresh/logout/change/forgot/reset), `TokenStorageService`, JWT decode util, functional `authInterceptor` (attaches bearer token, refreshes once on 401 and retries, redirects to `/login` on failure). |
| **Guards / RBAC** | `authGuard`, `roleGuard(roles)`, `*appHasRole` structural directive for role-gated nav. |
| **Pages** | Login, forgot-password, reset-password, change-password (forced-expiry flow), placeholder dashboard. |
| **Shell** | Top nav with role-gated links, notification bell (unread badge, dropdown list, mark-as-read/mark-all-read), username, sign-out. |
| **Dev proxy** | `proxy.conf.json` forwards `/api` to the backend so the SPA and API share an origin in dev without needing CORS config. |

---

## 2. Defects Found & Fixed During Implementation

All of these were caught by actually exercising the running app against a real Postgres instance, not just by compiling:

1. **Failed-login counter never persisted.** `login()` was `@Transactional`; throwing `InvalidCredentialsException` rolled back the whole transaction, including the failed-attempt increment. Fixed by running the increment in its own `REQUIRES_NEW` transaction.
2. **`change-password` was accidentally public.** An over-broad route matcher (`/api/auth/**`) permitted every auth endpoint, including one that should require a valid token. Narrowed to only the truly public endpoints.
3. **`LazyInitializationException` on e-signature history and audit log reads.** Lazy JPA associations (`user`, `performedBy`) were accessed after the transaction/session had already closed (OSIV is disabled by design). Fixed by mapping entities to DTOs *inside* the `@Transactional` service methods, not in the controller — applied consistently across e-signature, audit log, notifications, and documents.
4. **`@Lob` on Postgres `TEXT` columns.** Caused a schema-validation mismatch (Hibernate expected `oid`/CLOB, the column is plain `TEXT`). Removed `@Lob`, used `columnDefinition = "TEXT"` instead.
5. **Local file storage path-traversal guard rejected valid keys.** The configured base path wasn't normalized before comparison, so a legitimately-resolved file path never matched. Fixed by normalizing to an absolute path at construction time.
6. **Drools compile failures.** `kie-spring` (Drools 7-era) was paired with Drools 10 core/compiler — a real version mismatch, since fixed by switching to `kie-api`. A second, separate gap surfaced only at runtime: Drools 10+ splits the MVEL consequence-compiler into its own artifact (`drools-mvel`), not pulled in transitively — rule *conditions* compiled fine, but any rule with a `then` block failed with `MissingDependencyException` until that dependency was added.
7. **Actuator's mail health check.** With `spring-boot-starter-mail` on the classpath, actuator auto-probes the configured SMTP host — which fails when email sending is intentionally disabled. Disabled via `management.health.mail.enabled=false`.
8. **Frontend TS field-initializer ordering.** `readonly form = this.fb.group(...)` as a class field ran before constructor-injected `fb` was assigned. Fixed by constructing forms with `FormGroup`/`FormControl` directly (no `FormBuilder` injection needed) rather than deferring to the constructor body.
9. **`@angular/animations` deprecated in this Angular version.** Confirmed (by removing it and rebuilding cleanly) that Angular Material in this version doesn't hard-require it — dropped the dependency and `provideAnimationsAsync()` entirely rather than carry a deprecated package.

---

## 3. Verification

- Every backend feature was exercised with `curl` against a live Postgres instance (not mocked), including negative cases: wrong passwords, lockout thresholds, expired/reused refresh tokens, unauthenticated/unauthorized access, malformed DRL.
- One integration test (`NotificationServiceIntegrationTest`) runs against the real DB rather than Testcontainers (see gap below) and passes.
- Frontend: `ng build` and `ng test` both pass; the auth shell was manually driven end-to-end in a browser (login → dashboard → notification bell → sign-out → guard redirect) and confirmed working visually.

---

## 4. Known Gaps / Open Items for Later Phases

- **No Docker in this environment**, so the project's default generated test (`CtmsBackendApplicationTests`, via `TestcontainersConfiguration`) cannot run here. It isn't broken — just unverified in this environment. Worth confirming CI has Docker before relying on it.
- **Notification preferences** (per-type email opt-in/out) were deliberately not built — no story required it yet, and adding schema for a hypothetical need would be premature. Revisit if a later phase's backlog story asks for it.
- **RBAC is enforced but only smoke-tested for ADMIN.** Other roles (Study Manager, CRA, etc.) don't have real permission boundaries to test yet since no domain entities exist until Phase 1+.
- **Email delivery is untested against a real SMTP server** — `notification.email.enabled=false` by default; flip it and point `SMTP_HOST`/`SMTP_PORT` at a real internal relay when ready, per the Implementation Plan's Open Items.
- **Drools rule authoring** is currently raw DRL via REST — Phase 10 builds the actual no-code visual editor on top of this same `RuleSet`/`RuleDefinition` backend.

---

## 5. Ready for Phase 1

Phase 0's exit criteria (per the Implementation Plan) are met: login, RBAC, audit, e-signature primitive, notifications, and document/versioning plumbing are all built, wired together, and verified against a real database — not just unit-tested in isolation. Phase 1 (Study Management) can now build directly on this foundation.
