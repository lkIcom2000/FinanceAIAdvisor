-- Story 8.3: read state and the prepared chat question of a Notification (FR-32). Reset deletes the rows.
ALTER TABLE notifications ADD COLUMN read_at TIMESTAMPTZ NULL;
ALTER TABLE notifications ADD COLUMN question TEXT NULL;
