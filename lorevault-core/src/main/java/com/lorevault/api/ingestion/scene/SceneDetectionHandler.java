package com.lorevault.api.ingestion.scene;

import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.timeline.infrastructure.CrossChapterBoundaryProjection;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
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
import com.lorevault.api.ingestion.resolution.event.TemporalEdgeWriteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Handler for scene detection stage of the ingestion pipeline.
 *
 * Listens to: ChapterIngestionEvent (legacy event from IngestionService)
 * Emits: ScenesDetectedEvent (on success) or IngestionFailedEvent (on failure)
 *
 * Implements {@link SceneDetectionOperation} so the CLI module can invoke
 * scene detection directly without going through Spring event dispatch.
 * The CLI provides the transaction context; this handler provides the logic.
 */
@Component
@Slf4j
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
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

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
        this.collectivePersistenceService = collectivePersistenceService;
        this.objectPersistenceService = objectPersistenceService;
        this.locationPersistenceService = locationPersistenceService;
        this.eventPersistenceService = eventPersistenceService;
        this.relationClaimPersistenceService = relationClaimPersistenceService;
        this.defaultTemporalEdgeService = defaultTemporalEdgeService;
        this.sceneTemporalRelationshipPersistenceService = sceneTemporalRelationshipPersistenceService;
        this.triadTemporalEdgeRequestFactory = triadTemporalEdgeRequestFactory;
        this.sceneRelationshipAnalysisService = sceneRelationshipAnalysisService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    /**
     * Event-driven entry point for the async pipeline.
     * Delegates to {@link #execute(UUID, UUID)} for the actual work,
     * then publishes failure events if the step returned an unsuccessful result.
     */
    @Async("sceneDetectionTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();

        log.info("[SCENE_DETECTION] Starting pipeline for job={}, chapter={}", jobId, chapterId);

        StepResult result = execute(jobId, chapterId);

        if (!result.success()) {
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, chapterId, "SCENE_DETECTION", result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    "SCENE_DETECTION failed: " + result.summary());
        }
    }

    /**
     * Synchronous scene detection logic, callable from CLI with an existing transaction.
     *
     * <p>When called from the event listener, the {@code @Async} + {@code AFTER_COMMIT}
     * wrapper handles transaction boundaries. When called from the CLI, the caller
     * must provide an active transaction (e.g., via {@code @Transactional} on the
     * orchestrator method).
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising scene detection outcome
     */
    @Override
    @Transactional
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();

        try {
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
                long elapsed = System.currentTimeMillis() - start;
                return StepResult.success("SCENE_DETECTION",
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
            var temporalDefaults = defaultTemporalEdgeService.createAllDefaults(bookId);

            Map<Integer, UUID> sceneIndexToId = scenes.stream()
                    .filter(scene -> scene.getSceneIndex() != null)
                    .collect(Collectors.toMap(
                            scene -> scene.getSceneIndex(),
                            scene -> scene.getEventId(),
                            (UUID left, UUID right) -> left
                    ));

            var sceneRelationshipOutcome = new TriadAnalysisModels.SceneRelationshipOutcome(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
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

            replayBoundaryTemporalProjection(jobId, temporalDefaults.newlyCreatedCrossChapterBoundaries());

            if (!scenes.isEmpty()) {
                individualPersistenceService.persistExtractedIndividuals(
                        scenes,
                        sceneRelationshipOutcome.sceneIndividualExtractions()
                );
                collectivePersistenceService.persistExtractedCollectives(
                        scenes,
                        sceneRelationshipOutcome.sceneCollectiveExtractions()
                );
                objectPersistenceService.persistExtractedObjects(
                        scenes,
                        sceneRelationshipOutcome.sceneObjectExtractions()
                );
                locationPersistenceService.persistExtractedLocations(
                        scenes,
                        sceneRelationshipOutcome.sceneLocationExtractions()
                );
                eventPersistenceService.persistExtractedEvents(
                        scenes,
                        sceneRelationshipOutcome.sceneEventExtractions()
                );
                relationClaimPersistenceService.persistExtractedRelationClaims(
                        scenes,
                        sceneRelationshipOutcome.sceneRelationClaimExtractions()
                );
            }

            stageSupport.updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    String.format("Detected %d semantic scenes from chapter text", scenes.size()));

            emitScenesDetected(jobId, chapterId, bookId, scenes);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success("SCENE_DETECTION",
                    String.format("Detected %d scenes", scenes.size()),
                    Map.of("scenesDetected", scenes.size()),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[SCENE_DETECTION] Failed for job={} chapter={}: {}", jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure("SCENE_DETECTION",
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure("SCENE_DETECTION",
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        }
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
        List<Scene> safeScenes = scenes != null ? scenes : List.of();
        List<UUID> sceneIds = safeScenes.stream().map(Scene::getEventId).toList();

        log.info("[SCENE_DETECTION] Emitting ScenesDetectedEvent: job={}, chapter={}, sceneCount={}",
                jobId, chapterId, safeScenes.size());

        eventPublisher.publishEvent(new ScenesDetectedEvent(this, jobId, chapterId, bookId, sceneIds));
    }

    private void replayBoundaryTemporalProjection(UUID jobId, List<CrossChapterBoundaryProjection> boundaries) {
        if (boundaries == null || boundaries.isEmpty()) {
            return;
        }

        for (CrossChapterBoundaryProjection boundary : boundaries) {
            if (boundary == null || boundary.getPreviousSceneId() == null || boundary.getNextSceneId() == null) {
                continue;
            }

            if (sceneTemporalRelationshipPersistenceService.hasAnyTemporalRelationshipBetween(
                    boundary.getPreviousSceneId(),
                    boundary.getNextSceneId())) {
                continue;
            }

            Chapter laterChapter = chapterRepo.findById(boundary.getNextChapterId()).orElse(null);
            if (laterChapter == null) {
                continue;
            }

            List<Scene> laterScenes = new ArrayList<>(sceneRepo.findByChapterId(boundary.getNextChapterId()));
            Scene firstScene = laterScenes.stream()
                    .filter(scene -> boundary.getNextSceneId().equals(scene.getEventId()))
                    .findFirst()
                    .orElse(null);
            if (firstScene == null) {
                continue;
            }

            laterChapter.setScenes(List.of(firstScene));

            var replayOutcome = sceneRelationshipAnalysisService.analyzeChapterTriadsWithIndividuals(
                    jobId,
                    laterChapter,
                    statusProps -> { }
            );

            List<TemporalEdgeWriteRequest> replayRequests = replayOutcome.triadAnalyses().stream()
                    .filter(analysis -> analysis.prevToCurrType() != null)
                    .map(analysis -> new TemporalEdgeWriteRequest(
                            boundary.getPreviousSceneId(),
                            boundary.getNextSceneId(),
                            analysis.prevToCurrType(),
                            analysis.prevToCurrCertainty(),
                            analysis.prevToCurrEvidence(),
                            analysis.timelineMarker(),
                            null
                    ))
                    .toList();
            sceneTemporalRelationshipPersistenceService.applyTemporalRelationships(replayRequests);
        }
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