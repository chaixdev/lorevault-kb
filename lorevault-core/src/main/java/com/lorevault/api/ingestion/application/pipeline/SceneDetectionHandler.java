package com.lorevault.api.ingestion.application.pipeline;

import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.domain.IngestionStatus;

import com.lorevault.api.ingestion.domain.SceneDetectionException;
import com.lorevault.api.ingestion.domain.SceneLocalizationException;
import com.lorevault.api.content.entities.Chapter;
import com.lorevault.api.content.entities.Scene;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.content.entities.SceneGraphRepository;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.application.triad.SceneRelationshipAnalysisService;
import com.lorevault.api.ingestion.application.triad.TriadTemporalEdgeRequestFactory;
import com.lorevault.api.ingestion.application.scene.SceneDetectionService;
import com.lorevault.api.ingestion.application.scene.SceneProcessingService;
import com.lorevault.api.ingestion.infrastructure.IndividualPersistenceService;
import com.lorevault.api.ingestion.infrastructure.LocationPersistenceService;
import com.lorevault.api.ingestion.infrastructure.EventPersistenceService;
import com.lorevault.api.ingestion.domain.triad.TriadAnalysisModels;
import com.lorevault.api.content.timeline.application.DefaultTemporalEdgeService;
import com.lorevault.api.content.timeline.application.SceneTemporalRelationshipPersistenceService;
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
    private final SceneTemporalRelationshipPersistenceService sceneTemporalRelationshipPersistenceService;
    private final TriadTemporalEdgeRequestFactory triadTemporalEdgeRequestFactory;
    private final SceneRelationshipAnalysisService sceneRelationshipAnalysisService;
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
            SceneTemporalRelationshipPersistenceService sceneTemporalRelationshipPersistenceService,
            TriadTemporalEdgeRequestFactory triadTemporalEdgeRequestFactory,
            SceneRelationshipAnalysisService sceneRelationshipAnalysisService,
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
        this.sceneTemporalRelationshipPersistenceService = sceneTemporalRelationshipPersistenceService;
        this.triadTemporalEdgeRequestFactory = triadTemporalEdgeRequestFactory;
        this.sceneRelationshipAnalysisService = sceneRelationshipAnalysisService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("sceneDetectionTaskExecutor")
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
            List<Scene> scenes = detectAndPersistScenes(jobId, chapter);
            
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

            var sceneRelationshipOutcome = new TriadAnalysisModels.SceneRelationshipOutcome(List.of(), List.of(), List.of(), List.of());
            if (!scenes.isEmpty()) {
                Chapter triadChapter = chapterRepo.findById(chapterId)
                        .orElseThrow(() -> new IllegalArgumentException("Chapter not found for triad analysis: " + chapterId));
                triadChapter.setScenes(List.copyOf(scenes));

                sceneRelationshipOutcome = sceneRelationshipAnalysisService.analyzeChapterTriadsWithIndividuals(
                        jobId,
                        triadChapter,
                        statusProps -> stageSupport.updateJobStatus(
                                jobId,
                                IngestionStatus.SCENE_TRIAD_ANALYSIS,
                                "Triad analysis for scenes [prev, curr, next]",
                                statusProps
                        )
                );
            }
            sceneTemporalRelationshipPersistenceService.applyTemporalRelationships(
                    triadTemporalEdgeRequestFactory.buildRequests(
                            chapterId,
                            sceneRelationshipOutcome.triadAnalyses(),
                            sceneIndexToId
                    )
            );

            if (!scenes.isEmpty()) {
                individualPersistenceService.persistExtractedIndividuals(
                        scenes,
                        sceneRelationshipOutcome.sceneIndividualExtractions()
                );
                locationPersistenceService.persistExtractedLocations(
                        scenes,
                        sceneRelationshipOutcome.sceneLocationExtractions()
                );
                eventPersistenceService.persistExtractedEvents(
                        scenes,
                        sceneRelationshipOutcome.sceneEventExtractions()
                );
            }
            
                    stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    String.format("Detected %d semantic scenes from chapter text", scenes.size()));
            
            emitScenesDetected(jobId, chapterId, bookId, scenes);

            return null;
                },
                this::isRetryableError
        );
    }

    private List<Scene> detectAndPersistScenes(UUID jobId, Chapter chapter) {
        UUID chapterId = chapter.getId();
        log.info("[SCENE_DETECTION] Detecting scenes for chapter {}", chapterId);

        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("[SCENE_DETECTION] Chapter {} has no text content", chapterId);
            return List.of();
        }

        // Use AI to detect scenes (passing jobId for status tracking)
        var segmentationOutcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
        var scenesWithCoords = segmentationOutcome.scenes();

        if (scenesWithCoords.isEmpty()) {
            log.info("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            return List.of();
        }

        // Persist detected scenes
        return sceneProcessingService.persistDetectedScenes(chapterId, scenesWithCoords);
    }

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
        if (e instanceof SceneDetectionException sceneDetectionException
                && sceneDetectionException.failure() != null) {
            String code = sceneDetectionException.failure().code();
            return "SCENE_DETECTION_RETRY_EXHAUSTED".equals(code)
                    || "SCENE_SEGMENT_NO_LOCALIZABLE_SCENES".equals(code)
                    || "SCENE_SEGMENTED_FALLBACK_EMPTY".equals(code)
                    || "SCENE_SEGMENTATION_XML_EMPTY".equals(code)
                    || "SCENE_COORDINATE_LOCALIZATION_EMPTY".equals(code)
                    || "SCENE_COORDINATE_LOCALIZATION_DROPPED_SCENES".equals(code);
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
