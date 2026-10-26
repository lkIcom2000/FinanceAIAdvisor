-- Story 3.8: the premise callout of a DETECT_TRAP answer, one sentence naming the unverified premise in the message's
-- own words; NULL on other messages.

ALTER TABLE messages ADD COLUMN premise TEXT;
