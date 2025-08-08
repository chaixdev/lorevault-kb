package com.lorevault.api.dto.ingestion;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for job status queries
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {

    /**
     * The unique identifier of the ingestion job
     */
    private UUID jobId;

    /**
     * The chapter being processed by this job
     */
    private UUID chapterId;

    /**
     * Current status of the job
     */
    private IngestionStatus currentStatus;

    /**
     * Progress percentage (0-100)
     */
    private Integer progressPercent;

    /**
     * When the job was created
     */
    private LocalDateTime createdAt;

    /**
     * When the job completed (null if still running)
     */
    private LocalDateTime completedAt;

    /**
     * Whether the job has finished (successfully or with error)
     */
    private Boolean isComplete;

    /**
     * Recent status updates (limited to avoid large responses)
     */
    private List<StatusUpdateDto> recentUpdates;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateDto {
        private IngestionStatus status;
        private String description;
        private LocalDateTime timestamp;
        private Integer progressPercent;
    }
}
