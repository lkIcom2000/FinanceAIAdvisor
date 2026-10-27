-- Story 8.2: Triggers detected by a monitoring run and the templated Notifications they produce (FR-30, FR-31, AD-16).
-- A Trigger key (persona_id, type, subject_id, period) exists once: a re-run skips it with all its outputs.
-- persona_id, trigger_id and action_id are id-only references to other modules or rows: no foreign keys.

CREATE TABLE triggers (
    id              UUID         PRIMARY KEY,
    persona_id      VARCHAR(64)  NOT NULL,
    type            VARCHAR(32)  NOT NULL
                    CHECK (type IN ('FORECAST_SHORTFALL', 'EF_BELOW_TARGET', 'DUPLICATE_COVERAGE', 'RULE_CHANGE',
                                    'RULE_ANNOUNCEMENT')),
    subject_id      VARCHAR(128) NOT NULL,
    period          VARCHAR(32)  NOT NULL,
    as_of           DATE         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (persona_id, type, subject_id, period)
);

-- outcome: INFORMED (L0), RECOMMENDED (L1), ACTION (linked Action; its state gives the status), ESCALATED (case).
CREATE TABLE notifications (
    id              UUID         PRIMARY KEY,
    persona_id      VARCHAR(64)  NOT NULL,
    trigger_id      UUID         NOT NULL,
    action_id       UUID,
    outcome         VARCHAR(16)  NOT NULL CHECK (outcome IN ('INFORMED', 'RECOMMENDED', 'ACTION', 'ESCALATED')),
    title           TEXT         NOT NULL,
    body            TEXT         NOT NULL,
    cited_prov_ids  JSONB        NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(cited_prov_ids) = 'array'),
    as_of           DATE         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    CHECK ((outcome = 'ACTION') = (action_id IS NOT NULL))
);

CREATE INDEX notifications_persona ON notifications (persona_id, created_at);
