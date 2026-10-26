-- Story 6.2: the assessment card. An ASK_PROFILE answer carries the card: the missing profile fields with values
-- pre-filled from account data (value, provId) and its status (OPEN, SAVED, CANCELLED). While a card is open, the
-- persona's dialog state is ASSESSMENT with {"assessmentId": ..., "fieldQueue": [...]} as payload (AD-24). Answers
-- are stored only by the profile module's confirm control.

ALTER TABLE messages ADD COLUMN assessment JSONB;
ALTER TABLE messages ADD CONSTRAINT messages_assessment_by_agent CHECK (assessment IS NULL OR author = 'AGENT');
