-- Story 13.5: the follow-up context of an agent message (FR-63): the depersonalised question of its turn, whether its
-- text was phrased by the LLM, and the provIds of its Core figures. The next turn builds its one previous turn from
-- it through the exposure filter; at exposure level none a template text never reaches the model, only its figures.
-- Rows written before this migration have none.

ALTER TABLE messages ADD COLUMN followup JSONB;
ALTER TABLE messages ADD CONSTRAINT messages_followup_by_agent
    CHECK (followup IS NULL OR author = 'AGENT');
