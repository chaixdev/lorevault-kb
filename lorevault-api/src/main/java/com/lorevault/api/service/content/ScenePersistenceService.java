package com.lorevault.api.service.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for persisting scene detection results to the database.
 * Separated from SceneDetectionService to ensure proper transaction boundaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenePersistenceService {

    private final ContentPersistencePort contentPersistencePort;

    /**
     * Persists the detected scenes to the database within a transaction.
     * This method is separate from AI detection to avoid long-running transactions
     * during external API calls.
     * 
     * @param chapterId The UUID of the chapter
     * @param scenesWithCoords The detected scenes with coordinates
     * @return List of created Scene entities
     */
    @Transactional
    public List<Scene> persistDetectedScenes(UUID chapterId, List<SceneWithCoordinates> scenesWithCoords) {
        log.debug("Persisting {} scenes for chapter {} (graph)", scenesWithCoords.size(), chapterId);
        if (scenesWithCoords.isEmpty()) return List.of();
        // Avoid duplicate persistence if scenes already exist
        if (!contentPersistencePort.findScenesByChapterId(chapterId).isEmpty()) {
            log.info("Chapter {} already has scenes; returning existing", chapterId);
            return contentPersistencePort.findScenesByChapterId(chapterId);
        }
        
        // Fetch chapter text to extract scene content
        String chapterText = contentPersistencePort.findChapterById(chapterId)
            .map(c -> c.getRawText())
            .orElse(null);
        
        final String finalChapterText = chapterText;
        List<Scene> scenes = scenesWithCoords.stream().map(s -> {
            Scene scene = new Scene();
            scene.setSceneIndex(s.sceneIndex());
            scene.setStartCharacterOffset(s.startCharacterOffset());
            scene.setEndCharacterOffset(s.endCharacterOffset());
            scene.setContextSummary(s.contextSummary());
            
            // Extract and set the scene text
            if (finalChapterText != null) {
                try {
                    int start = (int) s.startCharacterOffset();
                    int end = (int) s.endCharacterOffset();
                    if (start >= 0 && end <= finalChapterText.length() && start < end) {
                        String sceneText = finalChapterText.substring(start, end);
                        scene.setText(sceneText);
                        log.trace("Extracted scene text for scene {}: {} chars", s.sceneIndex(), sceneText.length());
                    } else {
                        log.warn("Invalid scene coordinates for scene {}: start={}, end={}, chapterLen={}", 
                               s.sceneIndex(), start, end, finalChapterText.length());
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract scene text for scene {}: {}", s.sceneIndex(), e.getMessage());
                }
            }
            
            return scene;
        }).collect(Collectors.toList());
        return contentPersistencePort.addScenesToChapter(chapterId, scenes);
    }

    /**
     * Deletes all scenes for a chapter to support clean retry after failure.
     * 
     * @param chapterId The chapter ID to delete scenes for
     * @return The number of scenes deleted
     */
    @Transactional
    public int deleteAllScenesForChapter(UUID chapterId) {
        log.debug("Deleting all scenes for chapter {} (graph)", chapterId);
        return contentPersistencePort.deleteScenesByChapterId(chapterId);
    }
}
