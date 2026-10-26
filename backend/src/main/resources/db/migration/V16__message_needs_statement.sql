-- Story 6.4: the demands-and-needs statement (IDD) of a RECOMMEND_CATEGORY answer on a protection topic: liability,
-- occupational disability and cover held twice, each with need, cover held and what to check, plus the Needs Profile
-- facts used and the statement's figures (FR-18, UX-DR44). Built by the Core; Home shows the latest one.

ALTER TABLE messages ADD COLUMN needs_statement JSONB;
ALTER TABLE messages ADD CONSTRAINT messages_needs_statement_by_agent
    CHECK (needs_statement IS NULL OR author = 'AGENT');
