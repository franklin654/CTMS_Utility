CREATE TABLE site (
    id                              BIGSERIAL PRIMARY KEY,
    study_id                        BIGINT        NOT NULL REFERENCES study(id),
    site_code                       VARCHAR(30)   NOT NULL,
    name                            VARCHAR(255)  NOT NULL,
    address_line1                   VARCHAR(255)  NOT NULL,
    address_line2                   VARCHAR(255),
    city                            VARCHAR(100)  NOT NULL,
    state_province                  VARCHAR(100),
    postal_code                     VARCHAR(20),
    country                         VARCHAR(100)  NOT NULL,
    principal_investigator_name     VARCHAR(255)  NOT NULL,
    principal_investigator_contact  VARCHAR(255)  NOT NULL,
    contact_name                    VARCHAR(255)  NOT NULL,
    contact_email                   VARCHAR(255)  NOT NULL,
    contact_phone                   VARCHAR(50)   NOT NULL,
    feasibility_status              VARCHAR(50)   NOT NULL,
    regulatory_information          VARCHAR(2000),
    status                          VARCHAR(30)   NOT NULL DEFAULT 'PENDING_ACTIVATION',
    activation_date                 DATE,
    assigned_cra_id                 BIGINT        REFERENCES users(id),
    created_by                      BIGINT        NOT NULL REFERENCES users(id),
    created_at                      TIMESTAMP     NOT NULL DEFAULT now(),
    modified_by                     BIGINT        NOT NULL REFERENCES users(id),
    modified_at                     TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_site_code ON site(site_code);
CREATE INDEX idx_site_study ON site(study_id);
CREATE INDEX idx_site_assigned_cra ON site(assigned_cra_id);

-- Fixed 5-item activation checklist per site (Java enum ChecklistItemType, not config-driven --
-- the BRD names an exact fixed list with no per-study variation, unlike Phase 2's genuinely
-- configurable document category/role tables).
CREATE TABLE site_activation_checklist_item (
    id             BIGSERIAL PRIMARY KEY,
    site_id        BIGINT       NOT NULL REFERENCES site(id),
    item_type      VARCHAR(50)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    completed_date DATE,
    note           VARCHAR(1000),
    updated_by     BIGINT       REFERENCES users(id),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (site_id, item_type)
);
CREATE INDEX idx_site_checklist_site ON site_activation_checklist_item(site_id);
