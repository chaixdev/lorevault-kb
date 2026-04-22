package com.lorevault.api.ingestion.application;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.ai.SceneLocalizationException;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ai.SceneDetectionService;
import com.lorevault.api.ai.SceneProcessingService;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.infrastructure.IndividualPersistenceService;
import com.lorevault.api.ingestion.infrastructure.LocationPersistenceService;
import com.lorevault.api.ingestion.infrastructure.EventPersistenceService;
import com.lorevault.api.timeline.DefaultTemporalEdgeService;
import com.lorevault.api.timeline.TriadEdgePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;
    private final SceneDetectionService sceneDetectionService;
    private final SceneProcessingService sceneProcessingService;
    private final IndividualPersistenceService individualPersistenceService;
    private final LocationPersistenceService locationPersistenceService;
    private final EventPersistenceService eventPersistenceService;
    private final DefaultTemporalEdgeService defaultTemporalEdgeService;
    private final TriadEdgePersistenceService triadEdgePersistenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public SceneDetectionHandler(
            ChapterGraphRepository chapterRepo,
            SceneGraphRepository sceneRepo,
            SceneDetectionService sceneDetectionService,
            SceneProcessingService sceneProcessingService,
            IndividualPersistenceService individualPersistenceService,
            LocationPersistenceService locationPersistenceService,
            EventPersistenceService eventPersistenceService,
            IngestionJobService ingestionJobService,
            DefaultTemporalEdgeService defaultTemporalEdgeService,
            TriadEdgePersistenceService triadEdgePersistenceService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.sceneDetectionService = sceneDetectionService;
        this.sceneProcessingService = sceneProcessingService;
        this.individualPersistenceService = individualPersistenceService;
        this.locationPersistenceService = locationPersistenceService;
        this.eventPersistenceService = eventPersistenceService;
        this.defaultTemporalEdgeService = defaultTemporalEdgeService;
        this.triadEdgePersistenceService = triadEdgePersistenceService;
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
            Chapter chapter = chapterRepo.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            
            UUID bookId = chapter.getBookId();
            
                    stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    "Analyzing chapter text with AI to identify semantic scene boundaries");

            // Check for existing scenes (idempotency)
            List<Scene> existingScenes = sceneRepo.findByChapterId(chapterId);
            if (!existingScenes.isEmpty()) {
                log.info("[SCENE_DETECTION] Found {} existing scenes for chapter {}, skipping detection", 
                        existingScenes.size(), chapterId);
                emitScenesDetected(jobId, chapterId, bookId, existingScenes);
                return null;
            }

            // Detect and persist new scenes
            DetectionPersistenceOutcome outcome = detectAndPersistScenes(jobId, chapter);
            List<Scene> scenes = outcome.persistedScenes();
            
            if (scenes.isEmpty()) {
                log.warn("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            }
            
            // Create default temporal edges
            log.info("[SCENE_DETECTION] Creating default temporal edges for book {}", bookId);
            defaultTemporalEdgeService.createAllDefaults(bookId);

            Map<Integer, UUID> sceneIndexToId = scenes.stream()
                    .filter(scene -> scene.getSceneIndex() != null)
                    .collect(Collectors.toMap(
                            scene -> scene.getSceneIndex(),
                            scene -> scene.getEventId(),
                            (UUID left, UUID right) -> left
                    ));
            triadEdgePersistenceService.applyTriadAnalysesPostPersistence(
                    chapterId,
                    outcome.triadAnalyses(),
                    sceneIndexToId
            );
            
                    stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    String.format("Detected %d semantic scenes from chapter text", scenes.size()));
            
            emitScenesDetected(jobId, chapterId, bookId, scenes);

            return null;
                },
                this::isRetryableError
        );
    }

    private DetectionPersistenceOutcome detectAndPersistScenes(UUID jobId, Chapter chapter) {
        UUID chapterId = chapter.getId();
        log.info("[SCENE_DETECTION] Detecting scenes for chapter {}", chapterId);

        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("[SCENE_DETECTION] Chapter {} has no text content", chapterId);
            return new DetectionPersistenceOutcome(List.of(), List.of());
        }

        // Use AI to detect scenes (passing jobId for status tracking)
        var detectionOutcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
        var scenesWithCoords = detectionOutcome.scenes();

        if (scenesWithCoords.isEmpty()) {
            log.info("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            return new DetectionPersistenceOutcome(List.of(), detectionOutcome.triadAnalyses());
        }

        // Persist detected scenes
        List<Scene> persistedScenes = sceneProcessingService.persistDetectedScenes(chapterId, scenesWithCoords);
        individualPersistenceService.persistExtractedIndividuals(
                persistedScenes,
                detectionOutcome.sceneIndividualExtractions()
        );
        locationPersistenceService.persistExtractedLocations(
                persistedScenes,
                detectionOutcome.sceneLocationExtractions()
        );
        eventPersistenceService.persistExtractedEvents(
                persistedScenes,
                detectionOutcome.sceneEventExtractions()
        );
        return new DetectionPersistenceOutcome(persistedScenes, detectionOutcome.triadAnalyses());
    }

    private record DetectionPersistenceOutcome(
            List<Scene> persistedScenes,
            List<com.lorevault.api.ai.TriadOrchestrationService.TriadAnalysis> triadAnalyses
    ) {}

    private void emitScenesDetected(UUID jobId, UUID chapterId, UUID bookId, List<Scene> scenes) {
        List<UUID> sceneIds = scenes.stream().map(Scene::getEventId).toList();
        
        log.info("[SCENE_DETECTION] Emitting ScenesDetectedEvent: job={}, chapter={}, sceneCount={}", 
                jobId, chapterId, scenes.size());
        
        eventPublisher.publishEvent(new ScenesDetectedEvent(this, jobId, chapterId, bookId, sceneIds));
    }

    private boolean isRetryableError(Exception e) {
        if (e instanceof SceneLocalizationException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (
                message.contains("LLM API") || 
                message.contains("scene detection failed") || 
                message.contains("Empty response") || 
                message.contains("timeout") ||
                message.contains("rate limit"));
    }

}
