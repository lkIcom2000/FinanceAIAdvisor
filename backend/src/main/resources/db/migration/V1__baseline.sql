-- Baseline: enable pgvector (0.8.2, pinned by the db image). Tables arrive with the stories that need them.
CREATE EXTENSION IF NOT EXISTS vector;
