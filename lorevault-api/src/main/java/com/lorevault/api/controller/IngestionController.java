package com.lorevault.api.controller;

import com.lorevault.api.dto.JobStatusResponse;
import com.lorevault.api.dto.SubmitChapterRequest;
import com.lorevault.api.dto.SubmitChapterResponse;
import com.lorevault.api.service.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for chapter ingestion and job management
 */
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * Submit a chapter for processing
     * 
     * @param request Chapter submission request
     * @return Job ID and chapter ID for tracking
     */
    @PostMapping("/chapters")
    public ResponseEntity<SubmitChapterResponse> submitChapter(@Valid @RequestBody SubmitChapterRequest request) {
        log.info("Received chapter submission request for: {} - {}", 
                request.getCoordinates(), request.getChapterTitle());
        
        try {
            SubmitChapterResponse response = ingestionService.submitChapter(request);
            log.info("Chapter submitted successfully. Job ID: {}, Chapter ID: {}", 
                    response.getJobId(), response.getChapterId());
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            log.error("Failed to submit chapter", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SubmitChapterResponse(null, null, "Failed to submit chapter: " + e.getMessage()));
        }
    }

    /**
     * Get the status of an ingestion job
     * 
     * @param jobId The job ID to check
     * @return Job status and progress information
     */
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        log.debug("Checking status for job ID: {}", jobId);
        
        Optional<JobStatusResponse> statusOpt = ingestionService.getJobStatus(jobId);
        
        if (statusOpt.isEmpty()) {
            log.warn("Job not found: {}", jobId);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(statusOpt.get());
    }

    /**
     * Health check endpoint specific to ingestion service
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Ingestion service is healthy");
    }
}
