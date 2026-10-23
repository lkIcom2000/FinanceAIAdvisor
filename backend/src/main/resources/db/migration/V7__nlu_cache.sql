-- Story 3.3: the advisory module's NLU cache (AD-5) and the suggestion chips of an agent message.
-- The cache holds the normalised NLU extraction of a message, keyed by sha256 over model tag, model digest, NLU prompt
-- hash and message. It is no persona state: no persona_id, never reset by persona reset.

CREATE TABLE nlu_cache (
    cache_key   CHAR(64)     PRIMARY KEY,
    extraction  TEXT         NOT NULL,
    parse_ok    BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Chip texts released with an agent message (0-3), e.g. the related question of the no-valid-source answer.
ALTER TABLE messages ADD COLUMN suggestions TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE messages ADD CONSTRAINT messages_suggestions_max CHECK (cardinality(suggestions) <= 3);
