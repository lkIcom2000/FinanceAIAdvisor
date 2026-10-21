-- Story 1.4: every persona has a simulated date and a fixture version.
-- 2026-10-20 / 'pre-fixtures' are placeholders until Story 2.1 seeds the per-persona fixtures.
UPDATE persona_state
SET simulated_date = DATE '2026-10-20',
    fixture_version = 'pre-fixtures'
WHERE simulated_date IS NULL OR fixture_version IS NULL;

ALTER TABLE persona_state
    ALTER COLUMN simulated_date SET NOT NULL,
    ALTER COLUMN fixture_version SET NOT NULL;
