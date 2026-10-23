-- Story 3.3 review: the NLU cache becomes persona state. An extraction can hold stated profile facts, which must not
-- outlive a persona reset (AD-23), so each row belongs to one persona and the cache key includes the persona (as well
-- as seed, numPredict and the schema hash). This replaces the V7 comment "no persona state, never reset": the advisory
-- module's persona reset now deletes the persona's rows. Rows written before this migration have no persona and are
-- dropped; they are only a cache.

DELETE FROM nlu_cache;

ALTER TABLE nlu_cache ADD COLUMN persona_id VARCHAR(64) NOT NULL;

CREATE INDEX nlu_cache_persona ON nlu_cache (persona_id);
