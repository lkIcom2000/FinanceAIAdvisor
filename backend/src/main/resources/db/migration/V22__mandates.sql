-- Story 9.1: rules (Mandates) a persona grants from a template with explicit bounds (FR-36, AD-10). ActionService is
-- the only writer; a rule is never deleted, only revoked (revoked_at, Story 9.4). created_at and revoked_at are dates
-- on the persona's simulated clock. floor is the source account's minimum balance after a transfer. persona_id and the
-- account ids are id-only references to other modules: no foreign keys.

CREATE TABLE mandates (
    id                 UUID           PRIMARY KEY,
    persona_id         VARCHAR(64)    NOT NULL,
    template           VARCHAR(32)    NOT NULL
                       CHECK (template IN ('SAFETY_CUSHION', 'EMERGENCY_FUND_TOP_UP')),
    max_per_action     NUMERIC(14, 2) NOT NULL CHECK (max_per_action > 0),
    max_per_month      NUMERIC(14, 2) NOT NULL CHECK (max_per_month >= max_per_action),
    source_account_id  UUID           NOT NULL,
    target_account_id  UUID           NOT NULL CHECK (target_account_id <> source_account_id),
    floor              NUMERIC(14, 2) NOT NULL CHECK (floor >= 0),
    created_at         DATE           NOT NULL,
    revoked_at         DATE           CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX mandates_persona ON mandates (persona_id, created_at);

-- One active rule per persona and template.
CREATE UNIQUE INDEX mandates_one_active ON mandates (persona_id, template) WHERE revoked_at IS NULL;

-- The rule an Action was executed under (set from Story 9.2).
ALTER TABLE actions ADD COLUMN mandate_id UUID REFERENCES mandates (id);
