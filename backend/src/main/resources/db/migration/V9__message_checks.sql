-- Story 3.4: the final check results an agent message is released with, shown under "Checks" on the response card.
-- A JSON array of {"id", "passed", "description"}: only applicable checks, empty for a template answer and for
-- user and advisor messages.

ALTER TABLE messages ADD COLUMN checks JSONB NOT NULL DEFAULT '[]';
ALTER TABLE messages ADD CONSTRAINT messages_checks_array CHECK (jsonb_typeof(checks) = 'array');
