-- Ad-hoc (unscheduled) visits are not generated from a protocol VisitTemplate -- a Site
-- Coordinator, Investigator, Study Manager, or Admin can request one directly for a subject
-- (e.g. AE follow-up, dose-modification check). visit_template_id becomes optional to allow this.
ALTER TABLE visit ALTER COLUMN visit_template_id DROP NOT NULL;
