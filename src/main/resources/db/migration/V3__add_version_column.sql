-- Add version column for optimistic locking on concurso table
ALTER TABLE concurso ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
