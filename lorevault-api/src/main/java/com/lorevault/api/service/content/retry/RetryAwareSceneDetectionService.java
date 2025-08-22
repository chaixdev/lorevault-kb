package com.lorevault.api.service.content.retry;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.SceneDetectionClient;
import com.lorevault.api.service.content.SceneDetectionXmlParser;
import com.lorevault.api.service.content.SceneCoordinateLocalizer;
import com.lorevault.api.service.ingestion.IngestionJobLifecycleService;
import com.lorevault.api.service.content.retry.LlmRetryStrategy.LlmRetryConfig;
import com.lorevault.api.service.content.retry.LlmRetryStrategy.LlmRetryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Enhanced scene detection service that provides better LLM retry handling
 * and communicates retry attempts through ingestion job status updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetryAwareSceneDetectionService {
    
    private final SceneDetectionClient sceneDetectionClient;
    private final SceneDetectionXmlParser xmlParser;
    private final SceneCoordinateLocalizer coordinateLocalizer;
    private final LlmRetryStrategy llmRetryStrategy;
    private final IngestionJobLifecycleService jobLifecycleService;
    
    /**
     * Detect scenes with retry logic and job status updates
     */
    public List<SceneWithCoordinates> detectScenesWithRetry(UUID jobId, UUID chapterId, String chapterText) {
        log.info("Starting retry-aware scene detection for chapter {} (job {})", chapterId, jobId);
        
        // Configure retry strategy for scene detection
        LlmRetryConfig retryConfig = LlmRetryConfig.defaultConfig();
        
        // Update job status to indicate retry attempt
        updateJobStatus(jobId, String.format("Detecting scenes with retry (up to %d attempts)", retryConfig.getMaxAttempts()));
        
        // Execute scene detection with retry
    LlmRetryResult<List<SceneWithCoordinates>> retryResult = llmRetryStrategy.executeWithRetry(
                "Scene Detection",
                retryConfig,
        () -> performFullSceneDetection(jobId, chapterId, chapterText)
        );
        
        if (retryResult.isSuccess()) {
            String successMsg = String.format("Scene detection succeeded after %d/%d attempts in %d ms", 
                                             retryResult.getAttemptsUsed(), retryConfig.getMaxAttempts(), 
                                             retryResult.getTotalDurationMs());
            updateJobStatus(jobId, successMsg);
            
            log.info("✅ Scene detection successful for chapter {}: {}", chapterId, successMsg);
            return retryResult.getResult();
            
        } else {
            String failureMsg = String.format("Scene detection failed after %d attempts in %d ms: %s", 
                                             retryResult.getAttemptsUsed(), retryResult.getTotalDurationMs(), 
                                             retryResult.getLastException().getMessage());
            updateJobStatus(jobId, failureMsg);
            
            log.error("❌ Scene detection failed for chapter {}: {}", chapterId, failureMsg);
            
            // Include retry attempt details in the exception for debugging
            String detailsMsg = String.join("; ", retryResult.getAttemptDetails());
            throw new RuntimeException("Scene detection failed with retry: " + failureMsg + " | Attempts: " + detailsMsg, 
                                     retryResult.getLastException());
        }
    }
    
    /**
     * Perform the complete scene detection pipeline (Two-pass LLM call + parsing + localization)
     */
    private List<SceneWithCoordinates> performFullSceneDetection(UUID jobId, UUID chapterId, String chapterText) {
        try {
            // Two-pass scene detection: Pass 1 (segmentation) -> Pass 2 (normalization)
            String finalXmlResponse = sceneDetectionClient.detectScenesTwoPass(jobId, chapterText);
            
            // Step 2: Parse XML response 
            List<SceneDetectionResult> sceneResults = xmlParser.parseResponse(finalXmlResponse, chapterText.length());
            
            // Validate parsing results - throw exception if empty to trigger retry
            if (sceneResults.isEmpty()) {
                throw new RuntimeException("Scene detection parsing returned empty results - likely malformed XML response");
            }
            
            // Step 3: Localize coordinates
            List<SceneWithCoordinates> scenes = coordinateLocalizer.localizeCoordinates(chapterText, sceneResults);
            
            // Final validation
            if (scenes.isEmpty()) {
                throw new RuntimeException("Scene coordinate localization returned empty results");
            }
            
            log.debug("Successfully completed two-pass scene detection pipeline: {} scenes detected", scenes.size());
            return scenes;
            
        } catch (Exception e) {
            // Log the specific stage that failed for debugging
            log.warn("Two-pass scene detection pipeline failed: {}", e.getMessage());
            throw e; // Re-throw to trigger retry
        }
    }
    
    /**
     * Update ingestion job status with retry progress
     */
    private void updateJobStatus(UUID jobId, String description) {
        try {
            jobLifecycleService.updateJobStatus(
                    jobId,
                    com.lorevault.api.domain.ingestion.IngestionStatus.DETECTING_SCENES,
                    description,
                    java.util.Map.of("timestamp", java.time.LocalDateTime.now().toString())
            );
        } catch (Exception e) {
            log.debug("Failed to update job status for job {}: {}", jobId, e.getMessage());
            // Don't fail the main operation if status update fails
        }
    }
}
