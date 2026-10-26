-- Story 3.7: calculated answers. A COMPUTE turn first releases a parameter proposal: the agent message carries the
-- SimulationSpec and its status (OPEN, CALCULATED, CANCELLED). The calculated answer lists the Core figures it used
-- (provId, value, unit, label), shown as figure chips. While a proposal is open, the persona's dialog state is
-- PARAMETER_PROPOSAL with {"proposalId": ...} as payload (AD-24).

ALTER TABLE messages ADD COLUMN proposal JSONB;
ALTER TABLE messages ADD COLUMN figures JSONB NOT NULL DEFAULT '[]';
ALTER TABLE messages ADD CONSTRAINT messages_figures_array CHECK (jsonb_typeof(figures) = 'array');
ALTER TABLE messages ADD CONSTRAINT messages_proposal_by_agent CHECK (proposal IS NULL OR author = 'AGENT');

ALTER TABLE dialog_states ADD CONSTRAINT dialog_states_known_state
    CHECK (state IN ('NONE', 'ASSESSMENT', 'PARAMETER_PROPOSAL'));
