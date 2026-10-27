-- Story 8.1: Actions proposed through the Autonomy Gate (FR-35, AD-10). ActionService is the only writer. level is
-- null only for an ESCALATED Action (no valid Rule Parameter to decide on). persona_id, the account ids and
-- trigger_id are id-only references to other modules: no foreign keys.

CREATE TABLE actions (
    id                 UUID           PRIMARY KEY,
    persona_id         VARCHAR(64)    NOT NULL,
    type               VARCHAR(32)    NOT NULL,
    regulatory_class   VARCHAR(16)    NOT NULL
                       CHECK (regulatory_class IN ('payment', 'investment', 'insurance', 'legal')),
    amount             NUMERIC(14, 2) CHECK (amount IS NULL OR amount >= 0),
    source_account_id  UUID,
    target_account_id  UUID,
    trigger_id         UUID,
    state              VARCHAR(32)    NOT NULL
                       CHECK (state IN ('INFORMED', 'RECOMMENDED', 'AWAITING_CONFIRMATION', 'EXECUTED', 'ESCALATED')),
    level              VARCHAR(2)     CHECK (level IN ('L0', 'L1', 'L2', 'L3')),
    capping_criteria   JSONB          NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(capping_criteria) = 'array'),
    as_of              DATE           NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    CHECK ((state = 'ESCALATED') = (level IS NULL)),
    CHECK (regulatory_class NOT IN ('insurance', 'legal') OR state <> 'EXECUTED')
);

CREATE INDEX actions_persona ON actions (persona_id, created_at);
