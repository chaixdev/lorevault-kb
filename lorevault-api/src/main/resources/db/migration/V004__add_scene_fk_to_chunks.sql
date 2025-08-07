-- Add scene_id foreign key column to chunks table
-- This enables the full Chapter -> Scene -> Chunk hierarchy

ALTER TABLE chunks 
ADD COLUMN scene_id UUID;

-- Add foreign key constraint
ALTER TABLE chunks 
ADD CONSTRAINT fk_chunks_scene 
    FOREIGN KEY (scene_id) 
    REFERENCES scenes(id) 
    ON DELETE CASCADE;

-- Create index for efficient scene-based queries
CREATE INDEX idx_chunks_scene ON chunks(scene_id);

-- Note: For v0.3.0 transition, chunks can temporarily exist with either:
-- 1. chapter_id only (legacy v0.2.0 chunks) 
-- 2. scene_id only (new v0.3.0+ chunks)
-- The application logic will handle this transition period.
