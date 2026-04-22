package com.lorevault.api.web.query.job;

import com.lorevault.api.web.query.job.JobStatusResponse;
import com.lorevault.api.web.query.job.JobListResponse;
import com.lorevault.api.web.ErrorResponse;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.JobStatusDetails;
import com.lorevault.api.ingestion.application.PaginatedJobSummaries;
import com.lorevault.api.ingestion.application.JobSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for job monitoring (Query operations)
 */
@RestController
@RequestMapping("/api/query/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Jobs", description = "Ingestion job monitoring and status")
public class JobsController {

    private final IngestionService ingestionService;

    /**
     * Get status of a specific job
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        log.debug("Job status request for jobId: {}", jobId);

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for jobId: {}", jobId);
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_JOB_ID")
                    .message("Job ID must be a valid UUID")
                    .details("providedJobId", jobId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/query/jobs/" + jobId)
                    .build()
            );
        }

        Optional<JobStatusDetails> statusOpt = ingestionService.getJobStatus(jobUuid);
        
        if (statusOpt.isEmpty()) {
            log.info("Job not found: {}", jobId);
            return ResponseEntity.notFound().build();
        }

        JobStatusDetails details = statusOpt.get();
        JobStatusResponse response = new JobStatusResponse();
        response.setJobId(details.jobId());
        response.setChapterId(details.chapterId());
        response.setBookId(details.bookId());
        response.setCurrentStatus(details.currentStatus());
        response.setProgressPercent(details.progressPercent());
        response.setIsComplete(details.isComplete());
        response.setCreatedAt(details.createdAt());
        response.setCompletedAt(details.completedAt());
        
        if (details.recentUpdates() != null) {
            response.setRecentUpdates(details.recentUpdates().stream()
                .map(u -> new JobStatusResponse.StatusUpdateDto(u.status(), u.description(), u.timestamp(), u.progressPercent()))
                .toList());
        }
        
        if (details.failureDetails() != null) {
            response.setFailureDetails(new JobStatusResponse.FailureDetails(
                details.failureDetails().code(),
                details.failureDetails().message(),
                details.failureDetails().exceptionType(),
                details.failureDetails().stage(),
                details.failureDetails().additionalDetails()
            ));
        }

        log.debug("Returning job status for jobId: {}, status: {}", 
                jobId, response.getCurrentStatus());
        
        return ResponseEntity.ok(response);
    }

    /**
     * List jobs with optional filters and pagination.
     */
    @GetMapping
    public ResponseEntity<?> listJobs(
            @RequestParam(value = "universe", required = false) String universe,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {

        log.info("List jobs request: universe={}, status={}, limit={}, offset={}",
                universe, status, limit, offset);

        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_PAGINATION")
                    .message("limit must be between 1 and 100")
                    .details("limit", String.valueOf(limit))
                    .timestamp(LocalDateTime.now())
                    .path("/api/query/jobs")
                    .build()
            );
        }
        if (offset < 0) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_PAGINATION")
                    .message("offset must be >= 0")
                    .details("offset", String.valueOf(offset))
                    .timestamp(LocalDateTime.now())
                    .path("/api/query/jobs")
                    .build()
            );
        }

        if (status != null && !status.isBlank() && !"ACTIVE".equalsIgnoreCase(status)) {
            // Basic validation - let service layer handle full enum validation
            String upperStatus = status.toUpperCase();
            if (!java.util.Set.of("PENDING", "IN_PROGRESS", "COMPLETE", "FAILED").contains(upperStatus)) {
                return ResponseEntity.badRequest().body(
                    ErrorResponse.builder()
                        .code("INVALID_STATUS")
                        .message("status must be one of ACTIVE, PENDING, IN_PROGRESS, COMPLETE, FAILED")
                        .details("status", status)
                        .timestamp(LocalDateTime.now())
                        .path("/api/query/jobs")
                        .build()
                );
            }
        }

        PaginatedJobSummaries summaries = ingestionService.listJobs(universe, status, limit, offset);
        
        JobListResponse response = new JobListResponse(
            summaries.jobs().stream().map(j -> {
                JobListResponse.JobSummary summary = new JobListResponse.JobSummary();
                summary.setJobId(j.jobId());
                summary.setChapterId(j.chapterId());
                summary.setBookId(j.bookId());
                summary.setChapterTitle(j.chapterTitle());
                summary.setUniverse(j.universe());
                summary.setSeries(j.series());
                summary.setBookNumber(j.bookNumber());
                summary.setChapterNumber(j.chapterNumber());
                summary.setStatus(j.status());
                summary.setProgress(j.progress());
                summary.setCreatedAt(j.createdAt());
                summary.setCompletedAt(j.completedAt());
                return summary;
            }).toList(),
            new JobListResponse.Pagination(
                summaries.pagination().total(),
                summaries.pagination().limit(),
                summaries.pagination().offset(),
                summaries.pagination().hasMore()
            )
        );
        
        return ResponseEntity.ok(response);
    }
}
