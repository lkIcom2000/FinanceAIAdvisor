-- Story 8.4: the customer reason of a capped Action (FR-39), written by propose and by a failed confirm.
ALTER TABLE actions ADD COLUMN reason TEXT NULL;
