package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.retry.LlmRetryStrategy;
import com.lorevault.api.service.content.retry.LlmRetryStrategy.LlmRetryConfig;
import com.lorevault.api.service.content.retry.LlmRetryStrategy.LlmRetryResult;
import com.lorevault.api.service.timeline.TriadEdgePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for AI-powered scene detection within chapter text.
 * 
 * This is a business logic service that orchestrates:
 * - LLM calls via SceneDetectionClient
 * - XML parsing and coordinate localization via SceneProcessingService
 * - Retry handling with status updates
 * - Triad analysis for temporal relationships
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneDetectionService {

    private final SceneDetectionClient sceneDetectionClient;
    private final SceneProcessingService sceneProcessingService;
    private final LlmRetryStrategy llmRetryStrategy;
    private final TriadOrchestrationService triadOrchestrationService;
    private final TriadEdgePersistenceService triadEdgePersistenceService;

    /**
     * Detect semantic scenes within chapter text using AI.
     * 
     * @param jobId The UUID of the ingestion job (for status tracking)
     * @param chapterId The UUID of the chapter
     * @param chapterText The full text content to analyze
     * @return List of detected scenes with their coordinates
     * @throws RuntimeException if the detection process fails
     */
    public List<SceneWithCoordinates> detectScenesInText(UUID jobId, UUID chapterId, String chapterText) {
        // Handle null or empty text gracefully
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content for scene detection", chapterId);
            return Collections.emptyList();
        }
        
        log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)", 
                 chapterId, jobId, chapterText.length());

        try {
            return detectScenesWithRetry(jobId, chapterId, chapterText);
        } catch (Exception e) {
            log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new RuntimeException("Scene detection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method: Detect scenes with retry logic and job status updates
     */
    private List<SceneWithCoordinates> detectScenesWithRetry(UUID jobId, UUID chapterId, String chapterText) {

        // Configure retry strategy for scene detection
        LlmRetryConfig retryConfig = LlmRetryConfig.defaultConfig();

        log.info("Scene segmentation (Pass 1) starting with retry (max {} attempts) for job {}",
                retryConfig.getMaxAttempts(), jobId);

        // Execute scene detection with retry
        LlmRetryResult<List<SceneWithCoordinates>> retryResult = llmRetryStrategy.executeWithRetry(
                "Scene Detection",
                retryConfig,
                () -> performFullSceneDetection(jobId, chapterId, chapterText));

        if (retryResult.isSuccess()) {
        String successMsg = String.format("Scene segmentation (Pass 1) succeeded after %d/%d attempts in %d ms",
                    retryResult.getAttemptsUsed(), retryConfig.getMaxAttempts(),
                    retryResult.getTotalDurationMs());

            log.info("Scene detection successful for chapter {}: {}", chapterId, successMsg);
            return retryResult.getResult();

        } else {
        String failureMsg = String.format("Scene segmentation (Pass 1) failed after %d attempts in %d ms: %s",
                    retryResult.getAttemptsUsed(), retryResult.getTotalDurationMs(),
                    retryResult.getLastException().getMessage());

            log.error("Scene detection failed for chapter {}: {}", chapterId, failureMsg);

            // Include retry attempt details in the exception for debugging
            String detailsMsg = String.join("; ", retryResult.getAttemptDetails());
            throw new RuntimeException(
                    "Scene detection failed with retry: " + failureMsg + " | Attempts: " + detailsMsg,
                    retryResult.getLastException());
        }
    }

    /**
     * Perform the complete scene detection pipeline (Pass 1 + triad-based Pass 2 + parsing + localization)
     */
    private List<SceneWithCoordinates> performFullSceneDetection(UUID jobId, UUID chapterId, String chapterText) {
        try {
            // DEADLOCK RISK: do NOT call ingestionJobService.updateJobStatus() inside this method.
            // Outer tx (SceneDetectionHandler) holds a read-lock on IngestionJob; updateJobStatus
            // uses REQUIRES_NEW and needs a write-lock on the same node → Neo4j deadlock.
            log.info("Scene segmentation (Pass 1): starting for job {} chapter {}", jobId, chapterId);

            // Pass 1: Initial scene segmentation with rich hints
            String pass1XmlResponse = sceneDetectionClient.detectScenesPass1(jobId, chapterText);

            // Step 2: Parse Pass 1 XML response to get initial scene segmentation
            List<SceneDetectionResult> sceneResults = sceneProcessingService.parseSceneDetectionXml(pass1XmlResponse, chapterText.length());

            // Validate parsing results - throw exception if empty to trigger retry
            if (sceneResults.isEmpty()) {
                throw new RuntimeException(
                        "Pass 1 scene detection parsing returned empty results - likely malformed XML response");
            }

            // Step 3: Localize coordinates from Pass 1 results
            List<SceneWithCoordinates> scenes = sceneProcessingService.localizeSceneCoordinates(chapterText, sceneResults);

            // Final validation
            if (scenes.isEmpty()) {
                throw new RuntimeException("Scene coordinate localization returned empty results");
            }

            // Triad Pass 2: analyze prev/curr/next scene triads and persist TEMPORAL edges
            // This replaces the old Pass 2 approach that sent full XML to LLM
            var chapter = com.lorevault.api.domain.content.Chapter.createWithReferences(
                null, null, null, null, null, chapterText, null
            );
            chapter.setId(chapterId);
            
            // Convert SceneWithCoordinates to Scene objects temporarily for triad analysis
            // (without persisting - that will happen later in the ingestion pipeline)
            var tempScenes = scenes.stream().map(s -> {
                var scene = new com.lorevault.api.domain.content.Scene();
                scene.setId(UUID.randomUUID()); // Temporary ID for triad analysis
                scene.setSceneIndex(s.sceneIndex());
                scene.setStartCharacterOffset(s.startCharacterOffset());
                scene.setEndCharacterOffset(s.endCharacterOffset());
                scene.setContextSummary(s.contextSummary());
                
                // Extract scene text from chapter
                if (chapterText != null) {
                    try {
                        int start = (int) s.startCharacterOffset();
                        int end = (int) s.endCharacterOffset();
                        if (start >= 0 && end <= chapterText.length() && start < end) {
                            String sceneText = chapterText.substring(start, end);
                            scene.setText(sceneText);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to extract scene text for triad analysis: {}", e.getMessage());
                    }
                }
                return scene;
            }).toList();
            
            // Add scenes to chapter for triad analysis (in-memory only)
            for (var scene : tempScenes) {
                chapter.addExistingScene(scene);
            }
            
            // Run triad orchestration with populated chapter
            log.info("Triad analysis (Pass 2): starting for job {} chapter {}", jobId, chapterId);
            var triadAnalyses = triadOrchestrationService.analyzeChapterTriads(jobId, chapter);
            triadEdgePersistenceService.applyTriadAnalyses(triadAnalyses);

            log.debug("Successfully completed triad-based scene detection pipeline: {} scenes detected, {} triad analyses completed", 
                     scenes.size(), triadAnalyses.size());
            return scenes;

        } catch (Exception e) {
            // Log the specific stage that failed for debugging with full stack trace
            log.error("Triad-based scene detection pipeline failed: {}", e.getMessage(), e);
            throw e; // Re-throw to trigger retry
        }
    }
}
