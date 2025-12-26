package com.lorevault.api.application.port;

import com.lorevault.api.dto.content.SceneWithCoordinates;

import java.util.List;
import java.util.UUID;

/**
 * Port for scene detection capabilities.
 * Abstracts away the specific AI/ML service used for scene detection.
 */
public interface SceneDetectionPort {
    
    /**
     * Detect semantic scenes within chapter text.
     * 
     * @param jobId The UUID of the ingestion job (for status tracking)
     * @param chapterId The UUID of the chapter
     * @param chapterText The full text content to analyze
     * @return List of detected scenes with their coordinates
     * @throws SceneDetectionException if the detection process fails
     */
    List<SceneWithCoordinates> detectScenesInText(UUID jobId, UUID chapterId, String chapterText);
    
    /**
     * Check if the scene detection service is currently available.
     * 
     * @return true if the service is available and ready to process requests
     */
    boolean isAvailable();
    
    /**
     * Get the current model or implementation being used.
     * Useful for logging and diagnostics.
     * 
     * @return A string identifier for the current scene detection implementation
     */
    String getImplementationInfo();
}
