package com.lorevault.api.web.ingestion;

import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.shared.ErrorResponse;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.service.ingestion.IngestionService;
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

        Optional<JobStatusResponse> status = ingestionService.getJobStatus(jobUuid);
        
        if (status.isEmpty()) {
            log.info("Job not found: {}", jobId);
            return ResponseEntity.notFound().build();
        }

        log.debug("Returning job status for jobId: {}, status: {}", 
                jobId, status.get().getCurrentStatus());
        
        return ResponseEntity.ok(status.get());
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
            try {
                IngestionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(
                    ErrorResponse.builder()
                        .code("INVALID_STATUS")
                        .message("status must be one of ACTIVE, " + java.util.Arrays.toString(IngestionStatus.values()))
                        .details("status", status)
                        .timestamp(LocalDateTime.now())
                        .path("/api/query/jobs")
                        .build()
                );
            }
        }

        JobListResponse response = ingestionService.listJobs(universe, status, limit, offset);
        return ResponseEntity.ok(response);
    }
}
