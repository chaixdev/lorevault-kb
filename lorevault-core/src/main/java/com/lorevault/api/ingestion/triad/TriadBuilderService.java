package com.lorevault.api.ingestion.triad;

import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.scene.SceneGraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Builds scene triads (previous, current, next) for per-scene triad analysis.
 * Uses NEXT_IN_READING_ORDER edges in the graph to resolve cross-chapter
 * adjacency, eliminating the need for in-memory chapter-scoped iteration.
 */
@Service
@Slf4j
public class TriadBuilderService {

    public record SceneTriad(Scene previous, Scene current, Scene next) {}

    private final SceneGraphRepository sceneRepo;

    public TriadBuilderService(SceneGraphRepository sceneRepo) {
        this.sceneRepo = sceneRepo;
    }

    /**
     * Build a scene triad for the given current scene.
     * Resolves {@code previous} and {@code next} via NEXT_IN_READING_ORDER
     * edges in the graph — naturally handles cross-chapter boundaries.
     *
     * @param currentSceneId the eventId of the current scene
     * @return a SceneTriad (previous and next may be null at book boundaries)
     */
    public SceneTriad buildTriad(UUID currentSceneId) {
        Scene curr = sceneRepo.findById(currentSceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + currentSceneId));

        Scene prev = findPreviousInReadingOrder(currentSceneId).orElse(null);
        Scene next = findNextInReadingOrder(currentSceneId).orElse(null);

        return new SceneTriad(prev, curr, next);
    }

    /**
     * Build a scene triad from already-loaded Scene objects.
     * Used by callers that iterate scenes in memory and track adjacency.
     */
    public SceneTriad buildTriad(Scene previous, Scene current, Scene next) {
        return new SceneTriad(previous, current, next);
    }

    public Optional<Scene> findPreviousInReadingOrder(UUID sceneId) {
        return sceneRepo.findPreviousSceneIdByReadingOrder(sceneId)
                .flatMap(sceneRepo::findById);
    }

    public Optional<Scene> findNextInReadingOrder(UUID sceneId) {
        return sceneRepo.findNextSceneIdByReadingOrder(sceneId)
                .flatMap(sceneRepo::findById);
    }

    public List<Scene> loadScenesForChapter(UUID chapterId) {
        return sceneRepo.findByChapterId(chapterId);
    }
}
