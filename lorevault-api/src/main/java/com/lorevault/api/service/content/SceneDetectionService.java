package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.SceneDetectionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for AI-powered scene detection within chapters.
 * Orchestrates scene detection using the SceneDetectionPort abstraction.
 * No longer handles persistence - that's delegated to ScenePersistenceService.
 * 
 * This service implements the v0.3.0 feature that transitions from deterministic
 * text chunking to AI-guided semantic segmentation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneDetectionService {

    private final ContentPersistencePort contentPersistencePort;
    private final SceneDetectionPort sceneDetectionPort;

    /**
     * Detects scenes within a chapter using AI analysis but does NOT persist them.
     * Uses the SceneDetectionPort abstraction to remain technology-agnostic.
     * <p>
     * This method is NOT transactional because it includes external API calls.
     * Database persistence should be handled separately via ScenePersistenceService.
     * 
     * @param chapterId The UUID of the chapter to analyze
     * @return List of SceneWithCoordinates ready for persistence
     * @throws IllegalArgumentException if chapter not found
     */
    public List<SceneWithCoordinates> detectScenesForChapter(UUID chapterId) {
        log.info("Starting scene detection for chapter: {}", chapterId);
        
        var chapterNode = contentPersistencePort.findChapterById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        String chapterText = chapterNode.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content, cannot detect scenes", chapterId);
            return List.of();
        }
        
        log.debug("Using scene detection implementation: {}", sceneDetectionPort.getImplementationInfo());
        
        try {
            // Delegate to the scene detection port implementation
            List<SceneWithCoordinates> scenesWithCoords = sceneDetectionPort.detectScenesInText(chapterId, chapterText);
            
            log.info("Successfully detected {} scenes for chapter {}", 
                    scenesWithCoords.size(), chapterId);
            
            return scenesWithCoords;
            
        } catch (Exception e) {
            log.error("Failed to detect scenes for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new RuntimeException("Scene detection failed for chapter " + chapterId, e);
        }
    }
}
