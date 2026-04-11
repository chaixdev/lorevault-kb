package com.lorevault.api.ingestion;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ai.SceneDetectionService;
import com.lorevault.api.ai.SceneProcessingService;
import com.lorevault.api.timeline.DefaultTemporalEdgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
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
public class SceneDetectionHandler {

    private static final Logger log = LoggerFactory.getLogger(SceneDetectionHandler.class);

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;
    private final SceneDetectionService sceneDetectionService;
    private final SceneProcessingService sceneProcessingService;
    private final IndividualPersistenceService individualPersistenceService;
    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final DefaultTemporalEdgeService defaultTemporalEdgeService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public SceneDetectionHandler(
            ChapterGraphRepository chapterRepo,
            SceneGraphRepository sceneRepo,
            SceneDetectionService sceneDetectionService,
            SceneProcessingService sceneProcessingService,
            IndividualPersistenceService individualPersistenceService,
            ChapterIndividualResolutionService chapterIndividualResolutionService,
            IngestionJobService ingestionJobService,
            DefaultTemporalEdgeService defaultTemporalEdgeService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.sceneDetectionService = sceneDetectionService;
        this.sceneProcessingService = sceneProcessingService;
        this.individualPersistenceService = individualPersistenceService;
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
        this.defaultTemporalEdgeService = defaultTemporalEdgeService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        UUID jobId = readUuidProperty(event, "jobId");
        UUID chapterId = readUuidProperty(event, "chapterId");
        
        log.info("[SCENE_DETECTION] Starting pipeline for job={}, chapter={}", jobId, chapterId);
        
        stageSupport.runStage(
            this,
            "SCENE_DETECTION",
            jobId,
            chapterId,
            () -> {
            // Look up the chapter to get the bookId
            Chapter chapter = chapterRepo.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            
            UUID bookId = readUuidProperty(chapter, "bookId");
            
                    stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    "Analyzing chapter text with AI to identify semantic scene boundaries");

            // Check for existing scenes (idempotency)
            List<Scene> existingScenes = sceneRepo.findByChapterId(chapterId);
            if (!existingScenes.isEmpty()) {
                log.info("[SCENE_DETECTION] Found {} existing scenes for chapter {}, skipping detection", 
                        existingScenes.size(), chapterId);
                chapterIndividualResolutionService.resolveChapter(chapterId);
                emitScenesDetected(jobId, chapterId, bookId, existingScenes);
                return null;
            }

            // Detect and persist new scenes
            List<Scene> scenes = detectAndPersistScenes(jobId, chapterId);
            chapterIndividualResolutionService.resolveChapter(chapterId);
            
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

        Chapter chapter = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        String chapterText = readStringProperty(chapter, "rawText");
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("[SCENE_DETECTION] Chapter {} has no text content", chapterId);
            return List.of();
        }

        // Use AI to detect scenes (passing jobId for status tracking)
        var detectionOutcome = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);
        var scenesWithCoords = detectionOutcome.scenes();

        if (scenesWithCoords.isEmpty()) {
            log.info("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            return List.of();
        }

        // Persist detected scenes
        List<Scene> persistedScenes = sceneProcessingService.persistDetectedScenes(chapterId, scenesWithCoords);
        individualPersistenceService.persistExtractedIndividuals(
                persistedScenes,
                detectionOutcome.sceneIndividualExtractions()
        );
        return persistedScenes;
    }

    private void emitScenesDetected(UUID jobId, UUID chapterId, UUID bookId, List<Scene> scenes) {
        List<UUID> sceneIds = scenes.stream().map(Scene::getEventId).toList();
        
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

    private UUID readUuidProperty(Object target, String propertyName) {
        return (UUID) new BeanWrapperImpl(target).getPropertyValue(propertyName);
    }

    private String readStringProperty(Object target, String propertyName) {
        Object value = new BeanWrapperImpl(target).getPropertyValue(propertyName);
        return value == null ? null : value.toString();
    }
}
