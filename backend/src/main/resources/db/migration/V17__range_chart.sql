-- Story 7.1: the range chart of a calculated what-if answer (UX-DR17): the yearly 10th, 50th and 90th percentiles from
-- the Core, the projection's assumptions and the key of the middle outcome that "Why?" opens.

ALTER TABLE messages ADD COLUMN range_chart JSONB;
ALTER TABLE messages ADD CONSTRAINT messages_range_chart_by_agent
    CHECK (range_chart IS NULL OR author = 'AGENT');
