package com.lorevault.api.handler;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.event.ingestion.ScenesDetectedEvent;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.SceneProcessingService;
import com.lorevault.api.service.ingestion.IngestionJobService;
import com.lorevault.api.service.timeline.DefaultTemporalEdgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Handler for scene detection stage of the ingestion pipeline.
 * 
 * Listens to: ChapterIngestionEvent (legacy event from IngestionService)
 * Emits: ScenesDetectedEvent (on success) or IngestionFailedEvent (on failure)
 * 
 * Responsibilities:
 * - Bridge from legacy ingestion event to new pipeline
 * - Use AI to detect semantic scene boundaries in chapter text
 * - Persist detected scenes to the database
 * - Create default temporal edges between scenes
 * - Update job status throughout the process
 */
@Component
@Slf4j
public class SceneDetectionHandler {

    private final Neo4jContentPersistenceAdapter contentPersistencePort;
    private final SceneDetectionService sceneDetectionService;
    private final SceneProcessingService sceneProcessingService;
    private final DefaultTemporalEdgeService defaultTemporalEdgeService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public SceneDetectionHandler(
            Neo4jContentPersistenceAdapter contentPersistencePort,
            SceneDetectionService sceneDetectionService,
            SceneProcessingService sceneProcessingService,
            IngestionJobService ingestionJobService,
            DefaultTemporalEdgeService defaultTemporalEdgeService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.contentPersistencePort = contentPersistencePort;
        this.sceneDetectionService = sceneDetectionService;
        this.sceneProcessingService = sceneProcessingService;
        this.defaultTemporalEdgeService = defaultTemporalEdgeService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        
        log.info("[SCENE_DETECTION] Starting pipeline for job={}, chapter={}", jobId, chapterId);
        
        stageSupport.runStage(
            this,
            "SCENE_DETECTION",
            jobId,
            chapterId,
            () -> {
            // Look up the chapter to get the bookId
            Chapter chapter = contentPersistencePort.findChapterById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            
            UUID bookId = chapter.getBookId();
            
                    stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    "Analyzing chapter text with AI to identify semantic scene boundaries");

            // Check for existing scenes (idempotency)
            List<Scene> existingScenes = contentPersistencePort.findScenesByChapterId(chapterId);
            if (!existingScenes.isEmpty()) {
                log.info("[SCENE_DETECTION] Found {} existing scenes for chapter {}, skipping detection", 
                        existingScenes.size(), chapterId);
                emitScenesDetected(jobId, chapterId, bookId, existingScenes);
                return null;
            }

            // Detect and persist new scenes
            List<Scene> scenes = detectAndPersistScenes(jobId, chapterId);
            
            if (scenes.isEmpty()) {
                log.warn("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            }
            
            // Create default temporal edges
            log.info("[SCENE_DETECTION] Creating default temporal edges for book {}", bookId);
            defaultTemporalEdgeService.createAllDefaults(bookId);
            
                    stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    String.format("Detected %d semantic scenes from chapter text", scenes.size()));
            
            emitScenesDetected(jobId, chapterId, bookId, scenes);

            return null;
                },
                this::isRetryableError
        );
    }

    private List<Scene> detectAndPersistScenes(UUID jobId, UUID chapterId) {
        log.info("[SCENE_DETECTION] Detecting scenes for chapter {}", chapterId);

        Chapter chapter = contentPersistencePort.findChapterById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("[SCENE_DETECTION] Chapter {} has no text content", chapterId);
            return List.of();
        }

        // Use AI to detect scenes (passing jobId for status tracking)
        var scenesWithCoords = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);

        if (scenesWithCoords.isEmpty()) {
            log.info("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            return List.of();
        }

        // Persist detected scenes
        return sceneProcessingService.persistDetectedScenes(chapterId, scenesWithCoords);
    }

    private void emitScenesDetected(UUID jobId, UUID chapterId, UUID bookId, List<Scene> scenes) {
        List<UUID> sceneIds = scenes.stream().map(Scene::getId).toList();
        
        log.info("[SCENE_DETECTION] Emitting ScenesDetectedEvent: job={}, chapter={}, sceneCount={}", 
                jobId, chapterId, scenes.size());
        
        eventPublisher.publishEvent(new ScenesDetectedEvent(this, jobId, chapterId, bookId, sceneIds));
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("LLM API") || 
                message.contains("scene detection failed") || 
                message.contains("Empty response") || 
                message.contains("timeout") ||
                message.contains("rate limit"));
    }
}
