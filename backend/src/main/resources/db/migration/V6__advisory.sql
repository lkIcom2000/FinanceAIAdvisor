-- Story 3.1: the advisory module's chat tables (AD-12). One conversation per persona; a user message is saved
-- before its turn runs, the agent message is written in the turn's transaction together with its Audit Record.
-- persona_id is an id-only reference to the personas module: no foreign key. Foreign keys stay inside the module.

CREATE TABLE conversations (
    id         UUID         PRIMARY KEY,
    persona_id VARCHAR(64)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE messages (
    seq             BIGINT       GENERATED ALWAYS AS IDENTITY UNIQUE,
    id              UUID         PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES conversations (id),
    persona_id      VARCHAR(64)  NOT NULL,
    author          VARCHAR(16)  NOT NULL CHECK (author IN ('USER', 'AGENT', 'ADVISOR')),
    text            TEXT         NOT NULL,
    outcome         VARCHAR(32)  CHECK (outcome IN ('INFORM', 'ASK_PROFILE', 'RECOMMEND_CATEGORY', 'COMPUTE',
                                     'REFUSE_OUT_OF_SCOPE', 'DETECT_TRAP', 'ESCALATE')),
    -- At most one answer per user message: a second turn for the same message fails here and rolls back.
    reply_to        UUID         UNIQUE REFERENCES messages (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((author = 'AGENT') = (outcome IS NOT NULL)),
    CHECK (author <> 'USER' OR reply_to IS NULL)
);

CREATE INDEX messages_persona_seq ON messages (persona_id, seq);

CREATE TABLE dialog_states (
    persona_id      VARCHAR(64)  PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES conversations (id),
    state           VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    payload         JSONB
);
