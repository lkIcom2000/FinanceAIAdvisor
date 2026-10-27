-- Story 9.3: one-tap undo of a transfer Fine made within a rule (FR-38, AD-10). The undo is a compensating Action
-- linked by undoes_id (at most one per Action); the original becomes UNDONE. Nothing is deleted.

ALTER TABLE actions DROP CONSTRAINT actions_state_check;
ALTER TABLE actions ADD CONSTRAINT actions_state_check
    CHECK (state IN ('INFORMED', 'RECOMMENDED', 'AWAITING_CONFIRMATION', 'EXECUTED', 'ESCALATED', 'UNDONE'));

ALTER TABLE actions ADD COLUMN undoes_id UUID UNIQUE REFERENCES actions (id);
ALTER TABLE actions ADD CONSTRAINT actions_undo_executed CHECK (undoes_id IS NULL OR state = 'EXECUTED');
