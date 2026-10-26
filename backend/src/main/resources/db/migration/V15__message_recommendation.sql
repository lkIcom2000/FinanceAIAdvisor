-- Story 6.3: the Suitability Statement of a RECOMMEND_CATEGORY answer: the Core's category selection in plain words,
-- conflicts, profile facts used, selection figures, main risks and ex-ante cost figures (UX-DR43). Built from Core
-- results and the stored profile only; the card shows it in the collapsed "Why this fits you" panel.

ALTER TABLE messages ADD COLUMN recommendation JSONB;
ALTER TABLE messages ADD CONSTRAINT messages_recommendation_by_agent
    CHECK (recommendation IS NULL OR author = 'AGENT');
