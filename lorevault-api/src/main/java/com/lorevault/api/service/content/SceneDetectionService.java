package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.graph.port.ContentPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for AI-powered scene detection within chapters.
 * Orchestrates the scene detection pipeline: AI detection, coordinate localization.
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
    private final SceneDetectionClient sceneDetectionClient;
    private final SceneDetectionXmlParser xmlParser;
    private final SceneCoordinateLocalizer coordinateLocalizer;

    /**
     * Detects scenes within a chapter using AI analysis but does NOT persist them.
     * Implements the two-pass approach: AI identifies scenes with anchors,
     * then code calculates exact character positions.
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
        
        try {
            // Stage 1: AI Scene Identification (external API call - no transaction)
            log.debug("Stage 1: Calling AI for scene detection on {} characters", chapterText.length());
            String aiResponse = sceneDetectionClient.detectScenes(chapterText);
            
            // Parse the XML response
            List<SceneDetectionResult> aiResults = xmlParser.parseResponse(aiResponse, chapterText.length());
            
            if (aiResults.isEmpty()) {
                log.warn("No scenes detected for chapter {}", chapterId);
                return List.of();
            }
            
            // Stage 2: Coordinate Localization (pure computation - no transaction)
            log.debug("Stage 2: Localizing coordinates for {} detected scenes", aiResults.size());
            List<SceneWithCoordinates> scenesWithCoords = coordinateLocalizer.localizeCoordinates(chapterText, aiResults);
            
            log.info("Successfully detected {} scenes with coordinates for chapter {}", 
                    scenesWithCoords.size(), chapterId);
            
            return scenesWithCoords;
            
        } catch (Exception e) {
            log.error("Failed to detect scenes for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new RuntimeException("Scene detection failed for chapter " + chapterId, e);
        }
    }
}
