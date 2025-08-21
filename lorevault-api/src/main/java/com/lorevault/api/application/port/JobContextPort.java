package com.lorevault.api.application.port;

import java.util.UUID;

/**
 * Port interface for managing job context during processing workflows.
 * Provides thread-safe job ID management for status tracking and retry handling.
 */
public interface JobContextPort {

    /**
     * Set the current job ID for the current processing thread.
     * This allows downstream services to access job context for status updates.
     * 
     * @param jobId The UUID of the job being processed
     */
    void setCurrentJobId(UUID jobId);

    /**
     * Clear the current job ID from the current processing thread.
     * Should be called in finally blocks to prevent memory leaks.
     */
    void clearCurrentJobId();

    /**
     * Get the current job ID for the current processing thread.
     * 
     * @return The UUID of the current job, or null if no job is set
     */
    UUID getCurrentJobId();
}