-- Story 3.5: the escalation module's queue of cases for a human advisor (FR-41, FR-52, AD-12). A TURN case is
-- written in the turn's transaction when block routing returns ESCALATE; it keeps a snapshot copy of the question,
-- the blocked draft and the block reasons. TRIGGER cases (Epic 5) have no turn. persona_id and turn_id are id-only
-- references to other modules: no foreign keys.

CREATE TABLE escalation_cases (
    id            UUID         PRIMARY KEY,
    persona_id    VARCHAR(64)  NOT NULL,
    origin        VARCHAR(16)  NOT NULL CHECK (origin IN ('TURN', 'TRIGGER')),
    turn_id       UUID         UNIQUE,
    question      TEXT         NOT NULL,
    draft         TEXT,
    block_reasons JSONB        NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(block_reasons) = 'array'),
    reason_class  VARCHAR(32),
    status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((origin = 'TURN') = (turn_id IS NOT NULL))
);

CREATE INDEX escalation_cases_persona ON escalation_cases (persona_id, created_at);
