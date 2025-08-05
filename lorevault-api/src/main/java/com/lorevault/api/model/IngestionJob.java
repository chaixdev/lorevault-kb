package com.lorevault.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an asynchronous chapter ingestion job.
 * Tracks the processing of a chapter through the ingestion pipeline.
 */
@Entity
@Table(
    name = "ingestion_jobs",
    indexes = {
        @Index(name = "idx_ingestion_jobs_chapter", columnList = "chapterId"),
        @Index(name = "idx_ingestion_jobs_status", columnList = "currentStatus")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Foreign Key to the chapters record this job is processing
     */
    @Column(nullable = false)
    @NotNull
    private UUID chapterId;

    /**
     * The current status of the job for efficient querying
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private IngestionStatus currentStatus;

    /**
     * The complete, ordered list of all status records for this job,
     * providing a full audit trail
     */
    @OneToMany(mappedBy = "jobId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    private List<StatusRecord> statusHistory = new ArrayList<>();

    /**
     * Timestamp of the job's creation
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the job's termination (null until finished)
     */
    private LocalDateTime completedAt;

    /**
     * Progress percentage (0-100) based on current status
     */
    @Column(nullable = false)
    private Integer progressPercent = 0;
}
