package com.lorevault.api.controller;

import com.lorevault.api.dto.JobStatusResponse;
import com.lorevault.api.dto.ErrorResponse;
import com.lorevault.api.service.IngestionService;
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
@RequestMapping("/api/jobs")
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
                    .path("/api/jobs/" + jobId)
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
     * List jobs (placeholder for future implementation)
     */
    @GetMapping
    public ResponseEntity<?> listJobs(
            @RequestParam(value = "universe", required = false) String universe,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        
        log.info("List jobs request: universe={}, status={}, limit={}, offset={}", 
                universe, status, limit, offset);

        // For now, return not implemented
        return ResponseEntity.status(501).body(
            ErrorResponse.builder()
                .code("NOT_IMPLEMENTED")
                .message("Job listing functionality not yet implemented")
                .timestamp(LocalDateTime.now())
                .path("/api/jobs")
                .build()
        );
    }
}
