package com.lorevault.api.service;

import com.lorevault.api.dto.SceneWithCoordinates;
import com.lorevault.api.model.Chapter;
import com.lorevault.api.model.Scene;
import com.lorevault.api.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for persisting scene detection results to the database.
 * Separated from SceneDetectionService to ensure proper transaction boundaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenePersistenceService {

    private final ChapterRepository chapterRepository;

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
        log.debug("Starting transactional persistence of {} scenes for chapter {}", 
                 scenesWithCoords.size(), chapterId);
        
        // Re-load the chapter within the transaction
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        
        // Double-check that scenes haven't been created by another process
        if (!chapter.getScenes().isEmpty()) {
            log.info("Chapter {} already has {} scenes, returning existing scenes", 
                    chapterId, chapter.getScenes().size());
            return chapter.getScenes();
        }
        
        // Create scenes through Chapter aggregate
        List<Scene> createdScenes = createScenesFromCoordinates(chapter, scenesWithCoords);
        
        // Save the chapter (which will cascade to scenes)
        chapterRepository.save(chapter);
        
        log.debug("Successfully persisted {} scenes for chapter {} in transaction", 
                 createdScenes.size(), chapterId);
        
        return createdScenes;
    }
    
    /**
     * Creates Scene entities from coordinate data and adds them to the Chapter aggregate.
     * This method encapsulates the business logic for scene creation.
     * 
     * @param chapter The Chapter aggregate to add scenes to
     * @param scenesWithCoords List of scene coordinate data
     * @return List of created Scene entities
     */
    private List<Scene> createScenesFromCoordinates(Chapter chapter, List<SceneWithCoordinates> scenesWithCoords) {
        return scenesWithCoords.stream()
                .map(sceneData -> {
                    // Use Chapter aggregate's factory method to create and add scene
                    return chapter.addScene(
                        sceneData.sceneIndex(),
                        sceneData.startCharacterOffset(),
                        sceneData.endCharacterOffset(),
                        sceneData.contextSummary()
                    );
                })
                .toList();
    }
    
    /**
     * Deletes all scenes for a chapter to support clean retry after failure.
     * 
     * @param chapterId The chapter ID to delete scenes for
     * @return The number of scenes deleted
     */
    @Transactional
    public int deleteAllScenesForChapter(UUID chapterId) {
        log.debug("Deleting all scenes for chapter {}", chapterId);
        
        // Load the chapter within the transaction
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        
        // Get number of scenes for logging
        int sceneCount = chapter.getScenes().size();
        
        // Clear scenes collection
        chapter.getScenes().clear();
        
        // Save the chapter (which will cascade the scene removal)
        chapterRepository.save(chapter);
        
        log.info("Deleted {} scenes for chapter {}", sceneCount, chapterId);
        return sceneCount;
    }
}
