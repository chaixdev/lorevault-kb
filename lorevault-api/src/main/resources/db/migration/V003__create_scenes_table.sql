-- Create scenes table to support hierarchical Chapter -> Scene -> Chunk structure
-- This table represents semantic scenes identified by AI within chapters

CREATE TABLE scenes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL,
    scene_index INTEGER NOT NULL,
    context_summary TEXT,
    start_character_offset BIGINT NOT NULL,
    end_character_offset BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Foreign key constraint
    CONSTRAINT fk_scenes_chapter 
        FOREIGN KEY (chapter_id) 
        REFERENCES chapters(id) 
        ON DELETE CASCADE,
    
    -- Ensure scene indexes are unique per chapter
    CONSTRAINT uk_scenes_chapter_index 
        UNIQUE (chapter_id, scene_index),
    
    -- Ensure character offsets are valid
    CONSTRAINT chk_scenes_offsets 
        CHECK (start_character_offset >= 0 AND end_character_offset > start_character_offset)
);

-- Create indexes for efficient queries
CREATE INDEX idx_scenes_chapter ON scenes(chapter_id);
CREATE INDEX idx_scenes_position ON scenes(chapter_id, scene_index);
CREATE INDEX idx_scenes_offsets ON scenes(start_character_offset, end_character_offset);
