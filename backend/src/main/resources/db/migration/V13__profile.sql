-- Story 6.1: the profile module's Suitability and Needs Profiles (AD-12, AD-23). One row per confirmed field; the
-- profile module's confirm control is the only writer, so every row carries confirmed_at. Facts stated in a chat
-- message (StatedProfile) are never stored here. persona_id is an id-only reference: no foreign key.

CREATE TABLE suitability_profiles (
    persona_id   VARCHAR(64)  NOT NULL,
    field        VARCHAR(64)  NOT NULL,
    value        JSONB        NOT NULL,
    confirmed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (persona_id, field)
);

CREATE TABLE needs_profiles (
    persona_id   VARCHAR(64)  NOT NULL,
    field        VARCHAR(64)  NOT NULL,
    value        JSONB        NOT NULL,
    confirmed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (persona_id, field)
);
