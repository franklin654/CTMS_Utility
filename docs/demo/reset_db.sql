-- ============================================================================
-- CTMS Dev/Demo Database Reset Script
-- ============================================================================
-- Wipes all trial/transactional data and user accounts from a dev or demo
-- database so it's ready for a fresh `demo_seed.sql` run, WITHOUT touching
-- the Flyway-applied schema itself or the handful of tables that hold
-- baseline reference/config data rather than trial data (see "NOT touched"
-- below). Standalone plain SQL -- run directly against Postgres (psql,
-- pgAdmin, DBeaver, etc.), no running backend required.
--
-- FOR DEV/DEMO DATABASES ONLY. Never run this against a validated or
-- production environment -- CLAUDE.md's "no hard deletes" rule is about the
-- application's own runtime behavior (soft-delete/status flags, immutable
-- audit logs); it does not extend to an operator resetting a disposable
-- sandbox database between demos. If you are unsure whether the database
-- you're pointed at is a sandbox, stop and check first.
--
-- Usage:
--   psql "$DB_URL" -f docs/demo/reset_db.sql
-- Then re-seed:
--   psql "$DB_URL" -f docs/demo/demo_seed.sql
--
-- NOT touched (Flyway-seeded baseline config, not trial data -- demo_seed.sql
-- assumes these already exist and does not re-create them):
--   roles                          -- the fixed 12-role RBAC list (V1)
--   rule_set, rule_definition      -- ELIGIBILITY/WORKFLOW/PAYMENT default
--                                     Drools rule sets the engine requires to
--                                     function at all (V6/V9/V12)
--   document_category_access_rule  -- role-based document visibility rules (V4)
--   document_workflow_role         -- document review/approval role mapping (V4)
--
-- Everything else below is trial data, per-study configuration, or user
-- accounts -- all safely reconstructable by re-running demo_seed.sql (which
-- creates its own users) or by normal app usage.
--
-- IMPORTANT about `users`: rule_definition.created_by is a nullable FK to
-- users(id). TRUNCATE's CASCADE is table-wide, not row-based -- it empties
-- an ENTIRE dependent table if that table has any FK pointing at a truncated
-- table, regardless of whether any row actually references a to-be-deleted
-- user. So `TRUNCATE users CASCADE` would wipe all of rule_definition (the
-- actual Drools rule content the engine needs to function), even after
-- nulling created_by first. To avoid that, `users` is cleared with a plain
-- DELETE below (which IS row-based and enforces the FK per-row), after
-- nulling rule_definition.created_by so no row actually references a user
-- being deleted. Do not "simplify" this back into one TRUNCATE list.
-- ============================================================================

TRUNCATE TABLE
    adverse_event,
    audit_log,
    budget,
    budget_line_item,
    budget_version,
    document,
    document_requirement,
    document_review,
    document_version,
    eligibility_criterion,
    e_signature,
    milestone,
    monitoring_visit,
    monitoring_visit_report,
    notification,
    password_history,
    password_reset_token,
    payment,
    protocol_deviation,
    refresh_token,
    site,
    site_activation_checklist_item,
    study,
    study_status_history,
    subject,
    subject_eligibility_answer,
    subject_status_history,
    task,
    test_result,
    test_result_attachment,
    user_roles,
    visit,
    visit_template
RESTART IDENTITY CASCADE;

UPDATE rule_definition SET created_by = NULL;
DELETE FROM users;
ALTER SEQUENCE users_id_seq RESTART WITH 1;
