package com.lorevault.api.infrastructure.ai.openai;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.application.port.SceneDetectionException;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.retry.RetryAwareSceneDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    
    // TODO: Add jobId parameter to SceneDetectionPort interface in future iteration
    // For now, we'll extract jobId from a thread-local context or pass null
    private static final ThreadLocal<UUID> CURRENT_JOB_ID = new ThreadLocal<>();
    
    /**
     * Set the current job ID for this thread (temporary solution)
     */
    public static void setCurrentJobId(UUID jobId) {
        CURRENT_JOB_ID.set(jobId);
    }
    
    /**
     * Clear the current job ID for this thread
     */
    public static void clearCurrentJobId() {
        CURRENT_JOB_ID.remove();
    }
    
    @Override
    public List<SceneWithCoordinates> detectScenesInText(UUID chapterId, String chapterText) {
        try {
            log.debug("Starting OpenAI scene detection with retry for chapter {} (length={} chars)", 
                     chapterId, chapterText.length());
            
            // Get current job ID from thread-local context
            UUID jobId = CURRENT_JOB_ID.get();
            
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
        return "OpenAI Scene Detection with Enhanced Retry (GPT-4)";
    }
}
