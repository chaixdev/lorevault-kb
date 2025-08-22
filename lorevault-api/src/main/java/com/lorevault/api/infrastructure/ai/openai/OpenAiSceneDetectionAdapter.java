package com.lorevault.api.infrastructure.ai.openai;

import com.lorevault.api.application.port.JobContextPort;
import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.application.port.SceneDetectionException;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.retry.RetryAwareSceneDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI implementation of scene detection capabilities with enhanced retry handling.
 * Uses RetryAwareSceneDetectionService for improved resilience and job status communication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiSceneDetectionAdapter implements SceneDetectionPort {
    
    private final RetryAwareSceneDetectionService retryAwareSceneDetectionService;
    private final JobContextPort jobContextPort;
    
    // Keep static methods for backward compatibility during transition period
    // TODO: Remove these after all callers are refactored to use JobContextPort
    
    /**
     * @deprecated Use JobContextPort.setCurrentJobId() instead
     */
    @Deprecated(forRemoval = true)
    public static void setCurrentJobId(UUID jobId) {
        // This method is now a no-op since we use JobContextPort injection
        // Static access patterns should be refactored to dependency injection
    }
    
    /**
     * @deprecated Use JobContextPort.clearCurrentJobId() instead
     */
    @Deprecated(forRemoval = true)
    public static void clearCurrentJobId() {
        // This method is now a no-op since we use JobContextPort injection
        // Static access patterns should be refactored to dependency injection
    }
    
    @Override
    public List<SceneWithCoordinates> detectScenesInText(UUID chapterId, String chapterText) {
        try {
            // Handle null or empty text gracefully
            if (chapterText == null || chapterText.trim().isEmpty()) {
                log.warn("Chapter {} has no text content for scene detection", chapterId);
                return Collections.emptyList();
            }
            
            log.debug("Starting OpenAI scene detection with retry for chapter {} (length={} chars)", 
                     chapterId, chapterText.length());
            
            // Get current job ID from JobContextPort
            UUID jobId = jobContextPort.getCurrentJobId();
            
            if (jobId != null) {
                // Use retry-aware service with job status updates
                return retryAwareSceneDetectionService.detectScenesWithRetry(jobId, chapterId, chapterText);
            } else {
                // Fallback: use retry-aware service without job status updates
                log.warn("No job ID available for scene detection - status updates will be skipped");
                return retryAwareSceneDetectionService.detectScenesWithRetry(null, chapterId, chapterText);
            }
            
        } catch (Exception e) {
            log.error("OpenAI scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new SceneDetectionException("OpenAI scene detection failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check availability by ensuring the service is ready
            return retryAwareSceneDetectionService != null;
        } catch (Exception e) {
            log.debug("Availability check failed for OpenAI adapter: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getImplementationInfo() {
    return "OpenAI-compatible Scene Detection with Enhanced Retry";
    }
}
