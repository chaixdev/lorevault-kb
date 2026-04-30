package com.lorevault.api.ingestion.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.CompositeProperty;
import org.springframework.data.neo4j.core.schema.Node;

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
@Node("StatusRecord")
public class StatusRecord {
    @Id
    private UUID id;

    /**
     * Foreign Key linking to ingestion_jobs.id
     */
    private UUID jobId;

    /**
     * The precise time this status was recorded
     */
    @CreatedDate
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
     * Progress percentage (0-100) for this status update
     */
    private Integer progressPercent;

    /**
     * A flexible field to store structured metadata relevant to this event
     * (e.g., entities extracted, error details, performance metrics)
     */
    @CompositeProperty(prefix = "prop")
    private Map<String, String> properties;
}
