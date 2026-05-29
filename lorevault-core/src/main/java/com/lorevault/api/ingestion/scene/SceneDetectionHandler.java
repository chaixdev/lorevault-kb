package com.lorevault.api.ingestion.scene;

import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.common.error.ExceptionSanitizer;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ingestion.resolution.event.DefaultTemporalEdgeCreationResult;
import com.lorevault.api.ingestion.triad.SceneRelationshipAnalysisService;
import com.lorevault.api.ingestion.triad.TriadTemporalEdgeRequestFactory;
import com.lorevault.api.ingestion.infrastructure.IndividualPersistenceService;
import com.lorevault.api.ingestion.infrastructure.ObjectPersistenceService;
import com.lorevault.api.ingestion.infrastructure.CollectivePersistenceService;
import com.lorevault.api.ingestion.infrastructure.LocationPersistenceService;
import com.lorevault.api.ingestion.infrastructure.EventPersistenceService;
import com.lorevault.api.ingestion.infrastructure.RelationClaimPersistenceService;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
import com.lorevault.api.ingestion.resolution.event.DefaultTemporalEdgeService;
import com.lorevault.api.ingestion.resolution.event.SceneTemporalRelationshipPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler for scene detection stage of the ingestion pipeline.
 * <p>
 * Implements {@link SceneDetectionOperation} so the step-by-step execution controller can invoke
 * scene detection directly without going through Spring event dispatch.
 * The step-by-step execution controller provides the transaction context; this handler provides the logic.
 */
@Component
@Slf4j
@ForStage(StageKey.SCENE_SEGMENTATION)
public class SceneDetectionHandler implements SceneDetectionOperation {

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;
    private final SceneDetectionService sceneDetectionService;
    private final SceneProcessingService sceneProcessingService;
    private final IndividualPersistenceService individualPersistenceService;
    private final CollectivePersistenceService collectivePersistenceService;
    private final ObjectPersistenceService objectPersistenceService;
    private final LocationPersistenceService locationPersistenceService;
    private final EventPersistenceService eventPersistenceService;
    private final RelationClaimPersistenceService relationClaimPersistenceService;
    private final DefaultTemporalEdgeService defaultTemporalEdgeService;
    private final SceneTemporalRelationshipPersistenceService sceneTemporalRelationshipPersistenceService;
    private final TriadTemporalEdgeRequestFactory triadTemporalEdgeRequestFactory;
    private final SceneRelationshipAnalysisService sceneRelationshipAnalysisService;

    public SceneDetectionHandler(
            ChapterGraphRepository chapterRepo,
            SceneGraphRepository sceneRepo,
            SceneDetectionService sceneDetectionService,
            SceneProcessingService sceneProcessingService,
            IndividualPersistenceService individualPersistenceService,
            CollectivePersistenceService collectivePersistenceService,
            ObjectPersistenceService objectPersistenceService,
            LocationPersistenceService locationPersistenceService,
            EventPersistenceService eventPersistenceService,
            RelationClaimPersistenceService relationClaimPersistenceService,
            DefaultTemporalEdgeService defaultTemporalEdgeService,
            SceneTemporalRelationshipPersistenceService sceneTemporalRelationshipPersistenceService,
            TriadTemporalEdgeRequestFactory triadTemporalEdgeRequestFactory,
            SceneRelationshipAnalysisService sceneRelationshipAnalysisService
    ) {
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.sceneDetectionService = sceneDetectionService;
        this.sceneProcessingService = sceneProcessingService;
        this.individualPersistenceService = individualPersistenceService;
        this.collectivePersistenceService = collectivePersistenceService;
        this.objectPersistenceService = objectPersistenceService;
        this.locationPersistenceService = locationPersistenceService;
        this.eventPersistenceService = eventPersistenceService;
        this.relationClaimPersistenceService = relationClaimPersistenceService;
        this.defaultTemporalEdgeService = defaultTemporalEdgeService;
        this.sceneTemporalRelationshipPersistenceService = sceneTemporalRelationshipPersistenceService;
        this.triadTemporalEdgeRequestFactory = triadTemporalEdgeRequestFactory;
        this.sceneRelationshipAnalysisService = sceneRelationshipAnalysisService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();

        try {
            // Look up the chapter to get the bookId
            Chapter chapter = chapterRepo.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

            UUID bookId = chapter.getBookId();


            // Check for existing scenes (idempotency)
            List<Scene> existingScenes = sceneRepo.findByChapterId(chapterId);
            if (! existingScenes.isEmpty()) {
                log.info("[SCENE_DETECTION] Found {} existing scenes for chapter {}, skipping detection",
                        existingScenes.size(), chapterId);
                // Note: StageCompletedEvent is emitted by the caller
                long elapsed = System.currentTimeMillis() - start;
                return StepResult.success(StageKey.SCENE_SEGMENTATION,
                        String.format("Skipped — %d scenes already exist", existingScenes.size()),
                        Map.of("scenesDetected", existingScenes.size()),
                        elapsed);
            }

            // Detect and persist new scenes
            List<Scene> scenes = detectAndPersistScenes(jobId, chapter);

            if (scenes.isEmpty()) {
                log.warn("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            }

            // Create default temporal edges
            log.info("[SCENE_DETECTION] Creating default temporal edges for book {}", bookId);
            DefaultTemporalEdgeCreationResult temporalDefaults = defaultTemporalEdgeService.createAllDefaults(bookId);

            Map<Integer, UUID> sceneIndexToId = scenes.stream()
                    .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                    .collect(Collectors.toMap(
                            Scene :: getSceneIndex,
                            Scene :: getEventId,
                            (left, right) -> left
                    ));

            TriadAnalysisModels.SceneRelationshipOutcome sceneRelationshipOutcome = new TriadAnalysisModels.SceneRelationshipOutcome(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
            if ( !scenes.isEmpty()) {
                chapter.setScenes(List.copyOf(scenes));

                sceneRelationshipOutcome = sceneRelationshipAnalysisService.analyzeChapterTriads(
                        jobId,
                        chapter,
                        statusProps -> {
                        }
                );
            }
            sceneTemporalRelationshipPersistenceService.applyTemporalRelationships(
                    triadTemporalEdgeRequestFactory.buildRequests(
                            chapterId,
                            sceneRelationshipOutcome.triadAnalyses(),
                            sceneIndexToId,
                            ctx.stageId()
                    )
            );

            if (! scenes.isEmpty()) {
                individualPersistenceService.persistExtractedIndividuals(scenes, sceneRelationshipOutcome.sceneIndividualExtractions());
                collectivePersistenceService.persistExtractedCollectives(scenes, sceneRelationshipOutcome.sceneCollectiveExtractions());
                objectPersistenceService.persistExtractedObjects(scenes, sceneRelationshipOutcome.sceneObjectExtractions());
                locationPersistenceService.persistExtractedLocations(scenes, sceneRelationshipOutcome.sceneLocationExtractions());
                eventPersistenceService.persistExtractedEvents(scenes, sceneRelationshipOutcome.sceneEventExtractions());
                relationClaimPersistenceService.persistExtractedRelationClaims(scenes, sceneRelationshipOutcome.sceneRelationClaimExtractions());
            }

            // Note: ScenesDetectedEvent is emitted by the caller (handleChapterIngestion
            // or StepExecutionCommandController), not here — so that fireEvents=false
            // can suppress the cascade.

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.SCENE_SEGMENTATION,
                    String.format("Detected %d scenes", scenes.size()),
                    Map.of("scenesDetected", scenes.size()),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[SCENE_DETECTION] Failed for job={} chapter={}: {}", jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ?StepResult.retryableFailure(StageKey.SCENE_SEGMENTATION,
                    ExceptionSanitizer.sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.SCENE_SEGMENTATION,
                    ExceptionSanitizer.sanitizeMessage(e), elapsed);
        }
    }

    private List<Scene> detectAndPersistScenes(UUID jobId, Chapter chapter) {
        UUID chapterId = chapter.getId();
        log.info("[SCENE_DETECTION] Detecting scenes for chapter {}", chapterId);

        // Use AI to detect scenes (passing jobId for status tracking)
        SceneDetectionService.SceneSegmentationOutcome segmentationOutcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
        List<SceneWithCoordinates> scenesWithCoords = segmentationOutcome.scenes();

        if (scenesWithCoords.isEmpty()) {
            log.info("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            return List.of();
        }

        // Persist detected scenes
        return sceneProcessingService.persistDetectedScenes(chapterId, scenesWithCoords);
    }

    private boolean isRetryableError(Exception e) {
        // Structured exception types — always retryable
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
        // Transient infrastructure errors — retryable
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            // Connection refused, read timeout, I/O errors from HTTP client
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
            // HTTP 429 — rate limited
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpServerErrorException) {
            // HTTP 5xx — server errors are generally transient
            return true;
        }
        // Fallback: message-based matching for LLM-specific errors only.
        // Intentionally narrow — Neo4j/driver timeouts must NOT be classified as retryable.
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("llm api")
                || lowerMessage.contains("scene detection failed")
                || lowerMessage.contains("empty response")
                || lowerMessage.contains("rate limit");
    }

}