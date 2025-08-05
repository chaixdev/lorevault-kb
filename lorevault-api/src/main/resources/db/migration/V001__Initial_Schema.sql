-- LoreVault v0.1.0 Initial Schema
-- Creates tables for Chapter ingestion and Job lifecycle tracking

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Chapters table
CREATE TABLE chapters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- LoreCoordinates (embedded)
    universe VARCHAR(255) NOT NULL,
    series VARCHAR(255) NOT NULL,
    book_number INTEGER NOT NULL,
    part_number INTEGER,
    chapter_number INTEGER NOT NULL,
    
    -- Chapter content
    chapter_title VARCHAR(1000) NOT NULL,
    raw_text TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    
    -- Audit fields
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for chapters
CREATE INDEX idx_chapters_coordinates ON chapters (universe, series, book_number, chapter_number);
CREATE INDEX idx_chapters_content_hash ON chapters (content_hash);

-- Ingestion jobs table
CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chapter_id UUID NOT NULL REFERENCES chapters(id),
    current_status VARCHAR(50) NOT NULL,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITHOUT TIME ZONE
);

-- Create indexes for ingestion_jobs
CREATE INDEX idx_ingestion_jobs_chapter ON ingestion_jobs (chapter_id);
CREATE INDEX idx_ingestion_jobs_status ON ingestion_jobs (current_status);

-- Status records table for job history
CREATE TABLE status_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES ingestion_jobs(id),
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    step_description TEXT NOT NULL,
    progress_percent INTEGER NOT NULL,
    properties JSONB
);

-- Create indexes for status_records
CREATE INDEX idx_status_records_job_timestamp ON status_records (job_id, timestamp DESC);

-- Create a function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to auto-update updated_at on chapters
CREATE TRIGGER update_chapters_updated_at 
    BEFORE UPDATE ON chapters 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
