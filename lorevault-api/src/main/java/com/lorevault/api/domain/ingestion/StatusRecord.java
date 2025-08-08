package com.lorevault.api.domain.ingestion;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable record of a status change event in an ingestion job.
 * Provides audit trail and detailed progress tracking.
 */
@Entity
@Table(
    name = "status_records",
    indexes = {
        @Index(name = "idx_status_records_job_timestamp", columnList = "jobId, timestamp DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Foreign Key linking to ingestion_jobs.id
     */
    @Column(nullable = false)
    @NotNull
    private UUID jobId;

    /**
     * The precise time this status was recorded
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /**
     * The job state at this point in time
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private IngestionStatus status;

    /**
     * A short, user-friendly message for the event
     */
    @Column(nullable = false)
    @NotNull
    private String stepDescription;

    /**
     * Estimated completion percentage at this point (0-100)
     */
    @Column(nullable = false)
    @NotNull
    private Integer progressPercent;

    /**
     * A flexible field to store structured metadata relevant to this event
     * (e.g., entities extracted, error details, performance metrics)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties;
}
