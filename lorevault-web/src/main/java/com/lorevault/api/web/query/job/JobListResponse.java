package com.lorevault.api.web.query.job;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.ingestion.domain.IngestionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for listing ingestion jobs with optional filtering and pagination.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobListResponse {

    private List<JobSummary> jobs;
    private Pagination pagination;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobSummary {
        private UUID jobId;
        private UUID chapterId;
        private UUID bookId;
        private String universe;
        private String series;
        private Integer bookNumber;
        private Integer chapterNumber;
        private String chapterTitle;
        private IngestionStatus status;
        private Integer progress;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        private long total;
        private int limit;
        private int offset;
        private boolean hasMore;
    }
}
