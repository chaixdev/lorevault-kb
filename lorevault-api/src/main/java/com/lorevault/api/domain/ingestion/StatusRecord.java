package com.lorevault.api.domain.ingestion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable record of a status change event in an ingestion job.
 * Provides audit trail and detailed progress tracking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusRecord {
    private UUID id;

    /**
     * Foreign Key linking to ingestion_jobs.id
     */
    private UUID jobId;

    /**
     * The precise time this status was recorded
     */
    private LocalDateTime timestamp;

    /**
     * The job state at this point in time
     */
    private IngestionStatus status;

    /**
     * A short, user-friendly message for the event
     */
    private String stepDescription;

    /**
     * Estimated completion percentage at this point (0-100)
     */
    private Integer progressPercent;

    /**
     * A flexible field to store structured metadata relevant to this event
     * (e.g., entities extracted, error details, performance metrics)
     */
    private Map<String, Object> properties;
}
