-- ============================================================================
-- CTMS Demo Seed Script
-- ============================================================================
-- Standalone, portable seed data for a live demo. Written as plain SQL /
-- PL/pgSQL so it can be run against any Postgres instance (psql, pgAdmin,
-- DBeaver, etc.) that already has the CTMS schema applied via Flyway
-- (migrations V1-V15). It does NOT require the backend application to be
-- running -- run this directly against the target database.
--
-- Prerequisite: start the CTMS backend once against the target DB (so Flyway
-- applies all migrations and creates the schema), then stop it, then run this
-- script, then start the backend again for the demo.
--
-- All demo users share the password:  Demo2026!Pass
--
-- Design notes:
--  - This inserts directly into tables, bypassing the service layer, so it
--    does NOT trigger Drools rule evaluation, notifications, or automatic
--    audit-log entries for every row the way the real app would. A modest,
--    hand-picked set of illustrative audit_log rows is included at the end
--    so the Traceability Report (Phase 12) has real-looking history to show
--    for the entities the demo script points at.
--  - Several things are DELIBERATELY left "unfinished" so they can be
--    demonstrated LIVE through the UI during the demo, matching
--    docs/demo/DEMO_GUIDE.md exactly:
--      * Robert Kim has NO consent document yet (consent-gate demo)
--      * Aisha Bello is still ENROLLED, not withdrawn (withdrawal e-signature demo)
--      * Riverside Regional Medical Center's checklist is complete but the
--        site is still PENDING_ACTIVATION (site-activation e-signature demo)
--      * One payment is left PENDING (hold/release e-signature demo)
--  - Safe to re-run on a fresh/empty database. NOT idempotent -- running it
--    twice against the same database will fail on unique constraints
--    (usernames, study/site/subject codes). Truncate first if re-seeding.
-- ============================================================================

DO $$
DECLARE
    v_password_hash CONSTANT VARCHAR := '$2a$10$xrouHHEPzwoLLrTkfRUWOubIR0BzkCw5uKA25VfmVPQFocQgvMDy.'; -- Demo2026!Pass

    v_role_admin BIGINT;
    v_role_study_manager BIGINT;
    v_role_site_coordinator BIGINT;
    v_role_investigator BIGINT;
    v_role_cra BIGINT;
    v_role_finance BIGINT;
    v_role_auditor BIGINT;

    v_admin_id BIGINT;
    v_mgr_id BIGINT;
    v_coord_id BIGINT;
    v_investigator_id BIGINT;
    v_cra_id BIGINT;
    v_finance_id BIGINT;
    v_auditor_id BIGINT;

    v_study_id BIGINT;
    v_study_code VARCHAR;

    v_site1_id BIGINT; -- Metro General Hospital, ACTIVE
    v_site1_code VARCHAR;
    v_site2_id BIGINT; -- Riverside Regional, PENDING_ACTIVATION on purpose
    v_site2_code VARCHAR;

    v_tmpl_screening BIGINT;
    v_tmpl_treatment1 BIGINT;
    v_tmpl_followup BIGINT;

    v_subj_maria BIGINT;
    v_subj_maria_code VARCHAR;
    v_subj_robert BIGINT;
    v_subj_robert_code VARCHAR;
    v_subj_aisha BIGINT;
    v_subj_aisha_code VARCHAR;
    v_subj_james BIGINT;
    v_subj_james_code VARCHAR;
    v_subj_elena BIGINT;
    v_subj_elena_code VARCHAR;

    v_maria_visit1 BIGINT;
    v_maria_visit2 BIGINT;
    v_maria_visit3 BIGINT;
    v_robert_visit1 BIGINT;
    v_aisha_visit1 BIGINT;
    v_james_visit1 BIGINT;
    v_elena_visit1 BIGINT;

    v_doc_maria BIGINT;
    v_docver_maria BIGINT;
    v_doc_aisha BIGINT;
    v_docver_aisha BIGINT;
    v_doc_james BIGINT;
    v_docver_james BIGINT;

    v_james_ae_id BIGINT;
    v_james_ae_esig BIGINT;
    v_deviation_id BIGINT;

    v_budget_id BIGINT;
    v_budget_version_id BIGINT;

    v_payment_released_esig BIGINT;
    v_payment_released_id BIGINT;
    v_payment_pending_id BIGINT; -- deliberately left PENDING for live hold/release demo

    v_site1_activation_esig BIGINT;

    v_task_cra_assign BIGINT;
    v_task_ae_escalation BIGINT;
BEGIN

    -- ------------------------------------------------------------------
    -- 1. Roles (already seeded by V1 migration -- just look them up)
    -- ------------------------------------------------------------------
    SELECT id INTO v_role_admin FROM roles WHERE code = 'ADMIN';
    SELECT id INTO v_role_study_manager FROM roles WHERE code = 'STUDY_MANAGER';
    SELECT id INTO v_role_site_coordinator FROM roles WHERE code = 'SITE_COORDINATOR';
    SELECT id INTO v_role_investigator FROM roles WHERE code = 'INVESTIGATOR';
    SELECT id INTO v_role_cra FROM roles WHERE code = 'CRA_MONITOR';
    SELECT id INTO v_role_finance FROM roles WHERE code = 'FINANCE_MANAGER';
    SELECT id INTO v_role_auditor FROM roles WHERE code = 'QA_COMPLIANCE_AUDITOR';

    -- ------------------------------------------------------------------
    -- 2. Demo users (password for all: Demo2026!Pass)
    -- ------------------------------------------------------------------
    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.admin', 'demo.admin@ctms-demo.local', v_password_hash, 'Dana Cole')
    RETURNING id INTO v_admin_id;

    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.mgr', 'demo.mgr@ctms-demo.local', v_password_hash, 'Priya Nair')
    RETURNING id INTO v_mgr_id;

    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.coord', 'demo.coord@ctms-demo.local', v_password_hash, 'Jamie Ortiz')
    RETURNING id INTO v_coord_id;

    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.investigator', 'demo.investigator@ctms-demo.local', v_password_hash, 'Dr. Samuel Reyes')
    RETURNING id INTO v_investigator_id;

    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.cra', 'demo.cra@ctms-demo.local', v_password_hash, 'Taylor Brooks')
    RETURNING id INTO v_cra_id;

    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.finance', 'demo.finance@ctms-demo.local', v_password_hash, 'Morgan Lee')
    RETURNING id INTO v_finance_id;

    INSERT INTO users (username, email, password_hash, full_name)
    VALUES ('demo.auditor', 'demo.auditor@ctms-demo.local', v_password_hash, 'Chris Patel')
    RETURNING id INTO v_auditor_id;

    INSERT INTO user_roles (user_id, role_id) VALUES
        (v_admin_id, v_role_admin),
        (v_mgr_id, v_role_study_manager),
        (v_coord_id, v_role_site_coordinator),
        (v_investigator_id, v_role_investigator),
        (v_cra_id, v_role_cra),
        (v_finance_id, v_role_finance),
        (v_auditor_id, v_role_auditor);

    -- ------------------------------------------------------------------
    -- 3. Study (already in CONDUCT -- actively enrolling/treating)
    -- ------------------------------------------------------------------
    INSERT INTO study (
        name, protocol_id, protocol_version, phase, sponsor, status,
        planned_start_date, planned_end_date, actual_start_date, description,
        created_by, modified_by
    ) VALUES (
        'Oncology Combination Therapy Trial', 'ONCO-DEMO-001', '2.0', 'PHASE_III', 'Acme Pharmaceuticals', 'CONDUCT',
        CURRENT_DATE - INTERVAL '210 days', CURRENT_DATE + INTERVAL '520 days', CURRENT_DATE - INTERVAL '180 days',
        'Randomized combination-therapy trial for advanced solid tumors.',
        v_mgr_id, v_mgr_id
    ) RETURNING id INTO v_study_id;

    v_study_code := 'ST-' || lpad(v_study_id::text, 6, '0');
    UPDATE study SET study_code = v_study_code WHERE id = v_study_id;

    INSERT INTO study_status_history (study_id, from_status, to_status, justification, changed_by, changed_at) VALUES
        (v_study_id, NULL, 'DRAFT', 'Study created', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '210 days'),
        (v_study_id, 'DRAFT', 'ACTIVE', 'Protocol finalized, IRB approval received', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '195 days'),
        (v_study_id, 'ACTIVE', 'CONDUCT', 'First site activated, enrollment open', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '180 days');

    -- ------------------------------------------------------------------
    -- 4. Sites
    --    Site 1 (Metro General): fully activated, day-to-day walkthrough.
    --    Site 2 (Riverside): checklist complete but left PENDING_ACTIVATION
    --    on purpose -- there is no way to reach this state through the
    --    normal app flow (completing the last checklist item auto-activates
    --    silently), so it's seeded directly here specifically to demo the
    --    "Attempt Activation" e-signature dialog live.
    -- ------------------------------------------------------------------
    INSERT INTO site (
        study_id, site_code, name, address_line1, city, country,
        principal_investigator_name, principal_investigator_contact,
        contact_name, contact_email, contact_phone, feasibility_status,
        status, activation_date, created_by, modified_by
    ) VALUES (
        v_study_id, 'PLACEHOLDER1', 'Metro General Hospital', '400 Medical Plaza Dr', 'Chicago', 'USA',
        'Dr. Samuel Reyes', 'sreyes@metrogeneral-demo.local',
        'Jamie Ortiz', 'jortiz@metrogeneral-demo.local', '312-555-0142', 'Completed',
        'ACTIVE', CURRENT_DATE - INTERVAL '175 days', v_mgr_id, v_mgr_id
    ) RETURNING id INTO v_site1_id;

    v_site1_code := 'SITE-' || lpad(v_site1_id::text, 6, '0');
    UPDATE site SET site_code = v_site1_code WHERE id = v_site1_id;

    INSERT INTO site_activation_checklist_item (site_id, item_type, status, completed_date, updated_by) VALUES
        (v_site1_id, 'FEASIBILITY_COMPLETION', 'COMPLETE', CURRENT_DATE - INTERVAL '190 days', v_mgr_id),
        (v_site1_id, 'IRB_EC_APPROVAL', 'COMPLETE', CURRENT_DATE - INTERVAL '185 days', v_mgr_id),
        (v_site1_id, 'CONTRACT_COMPLETION', 'COMPLETE', CURRENT_DATE - INTERVAL '180 days', v_mgr_id),
        (v_site1_id, 'ESSENTIAL_DOCUMENTS_SUBMISSION', 'COMPLETE', CURRENT_DATE - INTERVAL '178 days', v_mgr_id),
        (v_site1_id, 'SITE_INITIATION_VISIT', 'COMPLETE', CURRENT_DATE - INTERVAL '175 days', v_mgr_id);

    INSERT INTO e_signature (user_id, entity_name, entity_id, reason, signed_at)
    VALUES (v_mgr_id, 'Site', v_site1_id::text, 'All activation prerequisites complete', CURRENT_TIMESTAMP - INTERVAL '175 days')
    RETURNING id INTO v_site1_activation_esig;
    UPDATE site SET esignature_id = v_site1_activation_esig WHERE id = v_site1_id;

    INSERT INTO site (
        study_id, site_code, name, address_line1, city, country,
        principal_investigator_name, principal_investigator_contact,
        contact_name, contact_email, contact_phone, feasibility_status,
        status, created_by, modified_by
    ) VALUES (
        v_study_id, 'PLACEHOLDER2', 'Riverside Regional Medical Center', '20 Riverside Ave', 'Portland', 'USA',
        'Dr. Amara Osei', 'aosei@riverside-demo.local',
        'Noah Kim', 'nkim@riverside-demo.local', '503-555-0198', 'Completed',
        'PENDING_ACTIVATION', v_mgr_id, v_mgr_id
    ) RETURNING id INTO v_site2_id;

    v_site2_code := 'SITE-' || lpad(v_site2_id::text, 6, '0');
    UPDATE site SET site_code = v_site2_code WHERE id = v_site2_id;

    INSERT INTO site_activation_checklist_item (site_id, item_type, status, completed_date, updated_by) VALUES
        (v_site2_id, 'FEASIBILITY_COMPLETION', 'COMPLETE', CURRENT_DATE - INTERVAL '10 days', v_mgr_id),
        (v_site2_id, 'IRB_EC_APPROVAL', 'COMPLETE', CURRENT_DATE - INTERVAL '9 days', v_mgr_id),
        (v_site2_id, 'CONTRACT_COMPLETION', 'COMPLETE', CURRENT_DATE - INTERVAL '8 days', v_mgr_id),
        (v_site2_id, 'ESSENTIAL_DOCUMENTS_SUBMISSION', 'COMPLETE', CURRENT_DATE - INTERVAL '6 days', v_mgr_id),
        (v_site2_id, 'SITE_INITIATION_VISIT', 'COMPLETE', CURRENT_DATE - INTERVAL '2 days', v_mgr_id);
    -- Deliberately no e-signature and status left PENDING_ACTIVATION -- see header note.

    -- ------------------------------------------------------------------
    -- 5. Visit templates (Site 1's study)
    -- ------------------------------------------------------------------
    INSERT INTO visit_template (study_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, created_by, modified_by)
    VALUES (v_study_id, 'Screening Visit', 1, 0, 3, 3, 'Vitals, bloodwork, informed consent review', 'ONSITE', v_mgr_id, v_mgr_id)
    RETURNING id INTO v_tmpl_screening;

    INSERT INTO visit_template (study_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, depends_on_visit_template_id, created_by, modified_by)
    VALUES (v_study_id, 'Treatment Visit 1', 2, 14, 3, 3, 'Dosing, vitals, adverse event review', 'ONSITE', v_tmpl_screening, v_mgr_id, v_mgr_id)
    RETURNING id INTO v_tmpl_treatment1;

    INSERT INTO visit_template (study_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, depends_on_visit_template_id, created_by, modified_by)
    VALUES (v_study_id, 'Follow-up Visit', 3, 30, 5, 5, 'Vitals, bloodwork, quality-of-life questionnaire', 'REMOTE', v_tmpl_treatment1, v_mgr_id, v_mgr_id)
    RETURNING id INTO v_tmpl_followup;

    -- ------------------------------------------------------------------
    -- 6. Subjects
    -- ------------------------------------------------------------------

    -- 6a. Maria Alvarez -- IN_TREATMENT, fully worked example (consent on
    -- file, 1 completed visit, 1 upcoming). Shows the "everything working
    -- normally" case.
    INSERT INTO subject (subject_code, study_id, site_id, first_name, last_name, date_of_birth, gender, contact_phone, contact_email, screening_date, status, created_by, modified_by)
    VALUES ('PLACEHOLDER', v_study_id, v_site1_id, 'Maria', 'Alvarez', '1978-03-14', 'FEMALE', '312-555-0111', 'maria.alvarez@demo-patient.local', CURRENT_DATE - INTERVAL '60 days', 'IN_TREATMENT', v_coord_id, v_coord_id)
    RETURNING id INTO v_subj_maria;
    v_subj_maria_code := 'SUBJ-' || lpad(v_subj_maria::text, 6, '0');
    UPDATE subject SET subject_code = v_subj_maria_code WHERE id = v_subj_maria;

    INSERT INTO subject_status_history (subject_id, from_status, to_status, reason_code, changed_by, changed_at) VALUES
        (v_subj_maria, NULL, 'SCREENED', NULL, v_coord_id, CURRENT_TIMESTAMP - INTERVAL '60 days'),
        (v_subj_maria, 'SCREENED', 'ENROLLED', 'Eligibility confirmed', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '58 days'),
        (v_subj_maria, 'ENROLLED', 'IN_TREATMENT', 'First dose administered', v_investigator_id, CURRENT_TIMESTAMP - INTERVAL '46 days');

    INSERT INTO document (category, title, owner_id, study_id, subject_id, created_at, updated_at)
    VALUES ('INFORMED_CONSENT', 'Informed Consent Form -- Maria Alvarez', v_coord_id, v_study_id, v_subj_maria, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '60 days')
    RETURNING id INTO v_doc_maria;
    INSERT INTO document_version (document_id, version_number, file_name, storage_path, content_type, size_bytes, checksum_sha256, status, uploaded_by, uploaded_at)
    VALUES (v_doc_maria, 1, 'consent-maria-alvarez.pdf', 'demo-seed/consent-maria-alvarez.pdf', 'application/pdf', 182_340,
            'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2', 'CURRENT', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '60 days')
    RETURNING id INTO v_docver_maria;
    UPDATE document SET current_version_id = v_docver_maria WHERE id = v_doc_maria;

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, actual_date, completed_at, created_by, modified_by)
    VALUES (v_subj_maria, v_tmpl_screening, 'Screening Visit', 1, 0, 3, 3, 'Vitals, bloodwork, informed consent review', 'ONSITE', CURRENT_DATE - INTERVAL '60 days', 'COMPLETED', CURRENT_DATE - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '60 days', v_coord_id, v_coord_id)
    RETURNING id INTO v_maria_visit1;

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, actual_date, completed_at, created_by, modified_by)
    VALUES (v_subj_maria, v_tmpl_treatment1, 'Treatment Visit 1', 2, 14, 3, 3, 'Dosing, vitals, adverse event review', 'ONSITE', CURRENT_DATE - INTERVAL '46 days', 'COMPLETED', CURRENT_DATE - INTERVAL '46 days', CURRENT_TIMESTAMP - INTERVAL '46 days', v_coord_id, v_coord_id)
    RETURNING id INTO v_maria_visit2;

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, created_by, modified_by)
    VALUES (v_subj_maria, v_tmpl_followup, 'Follow-up Visit', 3, 30, 5, 5, 'Vitals, bloodwork, quality-of-life questionnaire', 'REMOTE', CURRENT_DATE - INTERVAL '30 days', 'SCHEDULED', v_coord_id, v_coord_id)
    RETURNING id INTO v_maria_visit3;
    -- Note: this SCHEDULED visit's date is in the past relative to CURRENT_DATE
    -- by design (screening_date was backdated 60 days) -- it reads as "overdue,
    -- needs rescheduling," which is a fine, realistic detail for the demo.

    -- 6b. Robert Kim -- SCREENED, NO consent uploaded yet, visit still
    -- SCHEDULED. Deliberately left this way for the live consent-gate demo.
    INSERT INTO subject (subject_code, study_id, site_id, first_name, last_name, date_of_birth, gender, contact_phone, contact_email, screening_date, status, created_by, modified_by)
    VALUES ('PLACEHOLDER', v_study_id, v_site1_id, 'Robert', 'Kim', '1965-11-02', 'MALE', '312-555-0133', 'robert.kim@demo-patient.local', CURRENT_DATE - INTERVAL '5 days', 'SCREENED', v_coord_id, v_coord_id)
    RETURNING id INTO v_subj_robert;
    v_subj_robert_code := 'SUBJ-' || lpad(v_subj_robert::text, 6, '0');
    UPDATE subject SET subject_code = v_subj_robert_code WHERE id = v_subj_robert;

    INSERT INTO subject_status_history (subject_id, from_status, to_status, changed_by, changed_at)
    VALUES (v_subj_robert, NULL, 'SCREENED', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '5 days');

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, created_by, modified_by)
    VALUES (v_subj_robert, v_tmpl_screening, 'Screening Visit', 1, 0, 3, 3, 'Vitals, bloodwork, informed consent review', 'ONSITE', CURRENT_DATE - INTERVAL '5 days', 'SCHEDULED', v_coord_id, v_coord_id)
    RETURNING id INTO v_robert_visit1;

    -- 6c. Aisha Bello -- ENROLLED, consent on file, visit 1 completed.
    -- Deliberately NOT withdrawn -- for the live withdrawal e-signature demo.
    INSERT INTO subject (subject_code, study_id, site_id, first_name, last_name, date_of_birth, gender, contact_phone, contact_email, screening_date, status, created_by, modified_by)
    VALUES ('PLACEHOLDER', v_study_id, v_site1_id, 'Aisha', 'Bello', '1990-07-22', 'FEMALE', '312-555-0155', 'aisha.bello@demo-patient.local', CURRENT_DATE - INTERVAL '20 days', 'ENROLLED', v_coord_id, v_coord_id)
    RETURNING id INTO v_subj_aisha;
    v_subj_aisha_code := 'SUBJ-' || lpad(v_subj_aisha::text, 6, '0');
    UPDATE subject SET subject_code = v_subj_aisha_code WHERE id = v_subj_aisha;

    INSERT INTO subject_status_history (subject_id, from_status, to_status, reason_code, changed_by, changed_at) VALUES
        (v_subj_aisha, NULL, 'SCREENED', NULL, v_coord_id, CURRENT_TIMESTAMP - INTERVAL '20 days'),
        (v_subj_aisha, 'SCREENED', 'ENROLLED', 'Eligibility confirmed', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '18 days');

    INSERT INTO document (category, title, owner_id, study_id, subject_id, created_at, updated_at)
    VALUES ('INFORMED_CONSENT', 'Informed Consent Form -- Aisha Bello', v_coord_id, v_study_id, v_subj_aisha, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days')
    RETURNING id INTO v_doc_aisha;
    INSERT INTO document_version (document_id, version_number, file_name, storage_path, content_type, size_bytes, checksum_sha256, status, uploaded_by, uploaded_at)
    VALUES (v_doc_aisha, 1, 'consent-aisha-bello.pdf', 'demo-seed/consent-aisha-bello.pdf', 'application/pdf', 176_204,
            'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3', 'CURRENT', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '20 days')
    RETURNING id INTO v_docver_aisha;
    UPDATE document SET current_version_id = v_docver_aisha WHERE id = v_doc_aisha;

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, actual_date, completed_at, created_by, modified_by)
    VALUES (v_subj_aisha, v_tmpl_screening, 'Screening Visit', 1, 0, 3, 3, 'Vitals, bloodwork, informed consent review', 'ONSITE', CURRENT_DATE - INTERVAL '20 days', 'COMPLETED', CURRENT_DATE - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days', v_coord_id, v_coord_id)
    RETURNING id INTO v_aisha_visit1;

    -- 6d. James Whitfield -- ENROLLED, consent on file, has a fully
    -- resolved historical adverse event and a historical protocol
    -- deviation -- rich history to show in read-only views + traceability.
    INSERT INTO subject (subject_code, study_id, site_id, first_name, last_name, date_of_birth, gender, contact_phone, contact_email, screening_date, status, created_by, modified_by)
    VALUES ('PLACEHOLDER', v_study_id, v_site1_id, 'James', 'Whitfield', '1958-01-30', 'MALE', '312-555-0177', 'james.whitfield@demo-patient.local', CURRENT_DATE - INTERVAL '90 days', 'ENROLLED', v_coord_id, v_coord_id)
    RETURNING id INTO v_subj_james;
    v_subj_james_code := 'SUBJ-' || lpad(v_subj_james::text, 6, '0');
    UPDATE subject SET subject_code = v_subj_james_code WHERE id = v_subj_james;

    INSERT INTO subject_status_history (subject_id, from_status, to_status, reason_code, changed_by, changed_at) VALUES
        (v_subj_james, NULL, 'SCREENED', NULL, v_coord_id, CURRENT_TIMESTAMP - INTERVAL '90 days'),
        (v_subj_james, 'SCREENED', 'ENROLLED', 'Eligibility confirmed', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '88 days');

    INSERT INTO document (category, title, owner_id, study_id, subject_id, created_at, updated_at)
    VALUES ('INFORMED_CONSENT', 'Informed Consent Form -- James Whitfield', v_coord_id, v_study_id, v_subj_james, CURRENT_TIMESTAMP - INTERVAL '90 days', CURRENT_TIMESTAMP - INTERVAL '90 days')
    RETURNING id INTO v_doc_james;
    INSERT INTO document_version (document_id, version_number, file_name, storage_path, content_type, size_bytes, checksum_sha256, status, uploaded_by, uploaded_at)
    VALUES (v_doc_james, 1, 'consent-james-whitfield.pdf', 'demo-seed/consent-james-whitfield.pdf', 'application/pdf', 190_552,
            'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4', 'CURRENT', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '90 days')
    RETURNING id INTO v_docver_james;
    UPDATE document SET current_version_id = v_docver_james WHERE id = v_doc_james;

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, actual_date, completed_at, created_by, modified_by)
    VALUES (v_subj_james, v_tmpl_screening, 'Screening Visit', 1, 0, 3, 3, 'Vitals, bloodwork, informed consent review', 'ONSITE', CURRENT_DATE - INTERVAL '90 days', 'COMPLETED', CURRENT_DATE - INTERVAL '90 days', CURRENT_TIMESTAMP - INTERVAL '90 days', v_coord_id, v_coord_id)
    RETURNING id INTO v_james_visit1;

    -- Historical, fully resolved adverse event (with e-signature).
    INSERT INTO adverse_event (subject_id, visit_id, description, severity, status, resolution_notes, resolved_at, created_by, modified_by)
    VALUES (v_subj_james, v_james_visit1, 'Moderate nausea following first dose', 'MODERATE', 'RESOLVED',
            'Resolved after 48 hours with supportive care; no dose adjustment required.', CURRENT_TIMESTAMP - INTERVAL '85 days',
            v_coord_id, v_investigator_id)
    RETURNING id INTO v_james_ae_id;
    INSERT INTO e_signature (user_id, entity_name, entity_id, reason, signed_at)
    VALUES (v_investigator_id, 'AdverseEvent', v_james_ae_id::text, 'Resolved after supportive care; symptoms fully subsided', CURRENT_TIMESTAMP - INTERVAL '85 days')
    RETURNING id INTO v_james_ae_esig;
    UPDATE adverse_event SET esignature_id = v_james_ae_esig WHERE id = v_james_ae_id;

    -- Historical protocol deviation (log-only, no workflow).
    INSERT INTO protocol_deviation (subject_id, description, severity, deviation_date, created_by, modified_by)
    VALUES (v_subj_james, 'Treatment Visit 1 window missed by 4 days due to subject travel', 'MINOR', CURRENT_DATE - INTERVAL '76 days', v_coord_id, v_coord_id)
    RETURNING id INTO v_deviation_id;

    -- 6e. Elena Petrova -- newly screened, minimal history, just for
    -- realistic list-view volume.
    INSERT INTO subject (subject_code, study_id, site_id, first_name, last_name, date_of_birth, gender, contact_phone, contact_email, screening_date, status, created_by, modified_by)
    VALUES ('PLACEHOLDER', v_study_id, v_site1_id, 'Elena', 'Petrova', '1982-09-09', 'FEMALE', '312-555-0199', 'elena.petrova@demo-patient.local', CURRENT_DATE - INTERVAL '2 days', 'SCREENED', v_coord_id, v_coord_id)
    RETURNING id INTO v_subj_elena;
    v_subj_elena_code := 'SUBJ-' || lpad(v_subj_elena::text, 6, '0');
    UPDATE subject SET subject_code = v_subj_elena_code WHERE id = v_subj_elena;

    INSERT INTO subject_status_history (subject_id, from_status, to_status, changed_by, changed_at)
    VALUES (v_subj_elena, NULL, 'SCREENED', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '2 days');

    INSERT INTO visit (subject_id, visit_template_id, name, sequence_number, target_day, window_early_days, window_late_days, required_procedures, visit_type, scheduled_date, status, created_by, modified_by)
    VALUES (v_subj_elena, v_tmpl_screening, 'Screening Visit', 1, 0, 3, 3, 'Vitals, bloodwork, informed consent review', 'ONSITE', CURRENT_DATE - INTERVAL '2 days', 'SCHEDULED', v_coord_id, v_coord_id)
    RETURNING id INTO v_elena_visit1;

    -- ------------------------------------------------------------------
    -- 7. Budget + Payments
    -- ------------------------------------------------------------------
    INSERT INTO budget (study_id, created_by, modified_by) VALUES (v_study_id, v_finance_id, v_finance_id) RETURNING id INTO v_budget_id;
    INSERT INTO budget_version (budget_id, version_number, status, created_by)
    VALUES (v_budget_id, 1, 'CURRENT', v_finance_id) RETURNING id INTO v_budget_version_id;
    INSERT INTO budget_line_item (budget_version_id, cost_category, planned_amount, currency) VALUES
        (v_budget_version_id, 'MONITORING', 50000.00, 'USD'),
        (v_budget_version_id, 'SITE_PAYMENTS', 30000.00, 'USD'),
        (v_budget_version_id, 'INVESTIGATOR_FEES', 20000.00, 'USD');

    -- Already released (fully worked example, with e-signature).
    INSERT INTO e_signature (user_id, entity_name, entity_id, reason, signed_at)
    VALUES (v_finance_id, 'Payment', '0', 'placeholder', CURRENT_TIMESTAMP) RETURNING id INTO v_payment_released_esig;
    -- (entity_id fixed up below once the payment id is known)

    INSERT INTO payment (
        study_id, site_id, cost_category, event_code, trigger_entity_name, trigger_entity_id,
        base_amount, multiplier, amount, currency, status,
        release_reason, released_at, released_by, esignature_id,
        created_by, modified_by
    ) VALUES (
        v_study_id, v_site1_id, 'SITE_PAYMENTS', 'VISIT_COMPLETED', 'Visit', v_maria_visit1,
        500.00, 1.0, 500.00, 'USD', 'RELEASED',
        'Reviewed and approved for release', CURRENT_TIMESTAMP - INTERVAL '58 days', v_finance_id, v_payment_released_esig,
        v_coord_id, v_finance_id
    ) RETURNING id INTO v_payment_released_id;
    UPDATE e_signature SET entity_id = v_payment_released_id::text WHERE id = v_payment_released_esig;

    -- Deliberately left PENDING -- for the live hold -> release e-signature demo.
    INSERT INTO payment (
        study_id, site_id, cost_category, event_code, trigger_entity_name, trigger_entity_id,
        base_amount, multiplier, amount, currency, status,
        created_by, modified_by
    ) VALUES (
        v_study_id, v_site1_id, 'SITE_PAYMENTS', 'VISIT_COMPLETED', 'Visit', v_maria_visit2,
        500.00, 1.0, 500.00, 'USD', 'PENDING',
        v_coord_id, v_coord_id
    ) RETURNING id INTO v_payment_pending_id;

    -- ------------------------------------------------------------------
    -- 8. Tasks
    -- ------------------------------------------------------------------
    -- Open: Site 2 has no assigned CRA -- exactly the condition that would
    -- have auto-created this task had it gone through the real activation flow.
    INSERT INTO task (event_code, title, description, entity_name, entity_id, owner_id, owner_role, escalation_target_id, escalation_role, priority, status, due_at, created_by, modified_by)
    VALUES ('SITE_ACTIVATED', 'Assign CRA to newly activated site: ' || v_site2_code,
            'Site ' || v_site2_code || ' under study ' || v_study_code || ' is ready to activate but has no assigned CRA.',
            'Site', v_site2_id, v_admin_id, 'ADMIN', v_admin_id, 'ADMIN', 'MEDIUM', 'OPEN',
            CURRENT_TIMESTAMP + INTERVAL '3 days', v_mgr_id, v_mgr_id)
    RETURNING id INTO v_task_cra_assign;

    -- Completed: historical escalation task tied to James's (now resolved) AE.
    INSERT INTO task (event_code, title, description, entity_name, entity_id, owner_id, owner_role, escalation_target_id, escalation_role, priority, status, due_at, completed_at, created_by, modified_by)
    VALUES ('ADVERSE_EVENT_HIGH_SEVERITY', 'Review moderate adverse event for ' || v_subj_james_code,
            'Moderate nausea reported for subject ' || v_subj_james_code || ' following first dose.',
            'AdverseEvent', v_james_ae_id, v_investigator_id, 'INVESTIGATOR', v_mgr_id, 'STUDY_MANAGER', 'HIGH', 'COMPLETED',
            CURRENT_TIMESTAMP - INTERVAL '84 days', CURRENT_TIMESTAMP - INTERVAL '85 days', v_coord_id, v_investigator_id)
    RETURNING id INTO v_task_ae_escalation;

    -- ------------------------------------------------------------------
    -- 9. Illustrative audit-log entries
    --    (A representative subset, not a full replica of every side effect
    --    the real app would have logged -- enough for the Traceability
    --    Report demo to show real, populated history for the entities the
    --    demo guide points at.)
    -- ------------------------------------------------------------------
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, performed_at, before_value, after_value, reason) VALUES
        ('Study', v_study_id::text, 'CREATE', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '210 days', NULL, 'Oncology Combination Therapy Trial created', NULL),
        ('Study', v_study_id::text, 'STATE_CHANGE', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '195 days', 'DRAFT', 'ACTIVE', 'Protocol finalized, IRB approval received'),
        ('Study', v_study_id::text, 'STATE_CHANGE', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '180 days', 'ACTIVE', 'CONDUCT', 'First site activated, enrollment open'),
        ('Site', v_site1_id::text, 'CREATE', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '190 days', NULL, 'Metro General Hospital registered', NULL),
        ('Site', v_site1_id::text, 'STATE_CHANGE', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '175 days', 'PENDING_ACTIVATION', 'ACTIVE', 'all activation prerequisites complete'),
        ('Site', v_site2_id::text, 'CREATE', v_mgr_id, CURRENT_TIMESTAMP - INTERVAL '10 days', NULL, 'Riverside Regional Medical Center registered', NULL),
        ('Subject', v_subj_james::text, 'CREATE', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '90 days', NULL, 'enrolled subject ' || v_subj_james_code || ' under study ' || v_study_code, NULL),
        ('AdverseEvent', v_james_ae_id::text, 'CREATE', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '86 days', NULL, 'Moderate nausea following first dose', NULL),
        ('AdverseEvent', v_james_ae_id::text, 'STATE_CHANGE', v_investigator_id, CURRENT_TIMESTAMP - INTERVAL '85 days', 'OPEN', 'RESOLVED', 'Resolved after supportive care; symptoms fully subsided'),
        ('ProtocolDeviation', v_deviation_id::text, 'CREATE', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '76 days', NULL, 'Treatment Visit 1 window missed by 4 days', NULL),
        ('Payment', v_payment_released_id::text, 'CREATE', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '60 days', NULL, 'Payment generated from VISIT_COMPLETED', NULL),
        ('Payment', v_payment_released_id::text, 'STATE_CHANGE', v_finance_id, CURRENT_TIMESTAMP - INTERVAL '58 days', 'PENDING', 'RELEASED', 'Reviewed and approved for release'),
        ('Payment', v_payment_pending_id::text, 'CREATE', v_coord_id, CURRENT_TIMESTAMP - INTERVAL '46 days', NULL, 'Payment generated from VISIT_COMPLETED', NULL);

    -- ------------------------------------------------------------------
    -- Done. Print a cheat sheet of everything the demo guide references.
    -- ------------------------------------------------------------------
    RAISE NOTICE '=== CTMS DEMO SEED COMPLETE ===';
    RAISE NOTICE 'All demo users'' password: Demo2026!Pass';
    RAISE NOTICE 'Study: % (id %)', v_study_code, v_study_id;
    RAISE NOTICE 'Site 1 (Metro General, ACTIVE): % (id %)', v_site1_code, v_site1_id;
    RAISE NOTICE 'Site 2 (Riverside, PENDING_ACTIVATION, checklist complete): % (id %)', v_site2_code, v_site2_id;
    RAISE NOTICE 'Maria Alvarez (IN_TREATMENT, fully worked example): % (id %)', v_subj_maria_code, v_subj_maria;
    RAISE NOTICE 'Robert Kim (SCREENED, NO consent -- consent-gate demo): % (id %), visit id %', v_subj_robert_code, v_subj_robert, v_robert_visit1;
    RAISE NOTICE 'Aisha Bello (ENROLLED, not yet withdrawn -- withdrawal demo): % (id %)', v_subj_aisha_code, v_subj_aisha;
    RAISE NOTICE 'James Whitfield (resolved AE + protocol deviation history): % (id %)', v_subj_james_code, v_subj_james;
    RAISE NOTICE 'Elena Petrova (freshly screened): % (id %)', v_subj_elena_code, v_subj_elena;
    RAISE NOTICE 'Pending payment (hold/release demo): id %', v_payment_pending_id;
END $$;
