-- LoreVault v0.2.0 Content Storage & Segmentation
-- Adds chunks table for deterministic text segmentation

-- Chunks table
CREATE TABLE chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Chapter relationship
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    
    -- Position within chapter
    chunk_number_in_chapter INTEGER NOT NULL,
    start_char_in_chapter INTEGER NOT NULL,
    end_char_in_chapter INTEGER NOT NULL,
    
    -- Content identification
    content_hash VARCHAR(64) NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for chunks
CREATE INDEX idx_chunks_chapter ON chunks (chapter_id);
CREATE INDEX idx_chunks_position ON chunks (chapter_id, chunk_number_in_chapter);
CREATE INDEX idx_chunks_content_hash ON chunks (content_hash);

-- Add constraints
ALTER TABLE chunks ADD CONSTRAINT chunks_valid_position 
    CHECK (start_char_in_chapter >= 0 AND end_char_in_chapter > start_char_in_chapter);

ALTER TABLE chunks ADD CONSTRAINT chunks_valid_chunk_number 
    CHECK (chunk_number_in_chapter > 0);

-- Create trigger to auto-update updated_at on chunks
CREATE TRIGGER update_chunks_updated_at 
    BEFORE UPDATE ON chunks 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Create unique constraint for chapter + chunk number
CREATE UNIQUE INDEX idx_chunks_chapter_chunk_number 
    ON chunks (chapter_id, chunk_number_in_chapter);
