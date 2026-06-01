package com.lorevault.api.graph.event.scene;

import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.ai.llm.LlmRetryStrategy;
import com.lorevault.api.common.error.ExceptionSanitizer;
import com.lorevault.api.graph.collective.persistence.CollectivePersistenceService;
import com.lorevault.api.graph.event.persistence.EventPersistenceService;
import com.lorevault.api.graph.individual.persistence.IndividualPersistenceService;
import com.lorevault.api.graph.location.persistence.LocationPersistenceService;
import com.lorevault.api.graph.object.persistence.ObjectPersistenceService;
import com.lorevault.api.graph.relation.RelationClaimPersistenceService;
import com.lorevault.api.graph.timeline.DefaultTemporalEdgeCreationResult;
import com.lorevault.api.graph.timeline.DefaultTemporalEdgeService;
import com.lorevault.api.graph.timeline.SceneTemporalRelationshipPersistenceService;
import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.library.chunk.Chunk;
import com.lorevault.api.orchestration.job.IngestionFailure;
import com.lorevault.api.orchestration.job.IngestionFailureCarrier;
import com.lorevault.api.orchestration.pipeline.*;
import com.lorevault.api.orchestration.triad.SceneRelationshipAnalysisService;
import com.lorevault.api.orchestration.triad.TriadAnalysisException;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;
import com.lorevault.api.orchestration.triad.TriadTemporalEdgeRequestFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.lorevault.api.common.error.ExceptionSanitizer.safeMessage;

/**
 * Represents a semantic scene within a chapter. Scenes are identified by AI analysis
 * based on shifts in time, location, or character focus. They serve as the intermediate
 * level in the hierarchy: Chapter -> Scene -> Chunk.
 *
 * In the current graph model, Scene is also the persisted event carrier. Scenes therefore
 * keep the Event label and event-oriented accessors while a broader Event -> Entity model
 * remains future work.
 *
 * Scenes contain the exact character coordinates within the chapter text and provide
 * contextual boundaries for the chunking process in v0.3.0+.
 */
@Data
@NoArgsConstructor
@Node("Scene")
public class Scene {
    public static final String EVENT_LABEL = "Event";
    public static final String POTENTIAL_SPLIT_SCENE_START_LABEL = "PotentialSplitSceneStart";
    public static final String POTENTIAL_SPLIT_SCENE_END_LABEL = "PotentialSplitSceneEnd";

    @Id
    private UUID id;

    /**
     * Foreign key referencing the parent Chapter (aggregate root)
     */
    private Chapter chapter;

    private UUID chapterId;

    @Property("stageId")
    private UUID stageId;

    /**
     * The sequential index of the scene within the chapter (0-based, matching AI output)
     */
    private Integer sceneIndex;

    /**
     * AI-generated summary describing the context/content of this scene
     */
    private String contextSummary;

    /**
     * Temporal relationship hint extracted during scene analysis.
     */
    private String chronology;

    /**
     * Certainty for chronology hint extracted during scene analysis.
     */
    private String chronologyCertainty;

    /**
     * Verbatim temporal marker extracted during scene analysis.
     */
    private String chronologyMarker;

    /**
     * Zero-indexed character position where this scene starts in the chapter text
     */
    @Property("startOffset")
    private Long startCharacterOffset;

    /**
     * Zero-indexed character position where this scene ends in the chapter text
     */
    @Property("endOffset")
    private Long endCharacterOffset;

    /**
     * The actual text content of this scene, materialized for traceability and context
     * This supports the distributed content storage model where scenes store their own
     * text content to enable independent access without requiring chapter materialization.
     */
    private String text;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @DynamicLabels
    private List<String> labels = new ArrayList<>(List.of(EVENT_LABEL));

    /**
     * Chunks that belong to this scene (v0.3.0+)
     */
    @Relationship(type = "HAS_CHUNK")
    private List<Chunk> chunks = new ArrayList<>();

    @PersistenceCreator
    public Scene(UUID id,
                 Integer sceneIndex,
                 Long startCharacterOffset,
                 Long endCharacterOffset,
                 String contextSummary,
                 String chronology,
                 String chronologyCertainty,
                 String chronologyMarker,
                 String text,
                 UUID chapterId,
                 List<String> labels,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 List<Chunk> chunks,
                 Chapter chapter) {
        this.id = id;
        this.sceneIndex = sceneIndex;
        this.startCharacterOffset = startCharacterOffset;
        this.endCharacterOffset = endCharacterOffset;
        this.contextSummary = contextSummary;
        this.chronology = chronology;
        this.chronologyCertainty = chronologyCertainty;
        this.chronologyMarker = chronologyMarker;
        this.text = text;
        this.chapterId = chapterId;
        this.labels = labels == null ? new ArrayList<>(List.of(EVENT_LABEL)) : labels;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.chunks = chunks == null ? new ArrayList<>() : chunks;
        this.chapter = chapter;
    }

    public Scene(UUID id,
                 Integer sceneIndex,
                 Long startCharacterOffset,
                 Long endCharacterOffset,
                 String contextSummary,
                 String text,
                 UUID chapterId,
                 List<String> labels,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 List<Chunk> chunks,
                 Chapter chapter) {
        this(id, sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary,
                null, null, null, text, chapterId, labels, createdAt, updatedAt, chunks, chapter);
    }

    // =====================================
    // Business Methods
    // =====================================

    /**
     * Convenience method to get the length of the scene text
     */
    public long getTextLength() {
        return endCharacterOffset - startCharacterOffset;
    }

    /**
     * Extract the scene text from the provided chapter content
     */
    public String extractText(String chapterContent) {
        if (chapterContent == null || 
            startCharacterOffset >= chapterContent.length() || 
            endCharacterOffset > chapterContent.length()) {
            throw new IllegalArgumentException("Invalid character offsets for chapter content");
        }
        return chapterContent.substring(startCharacterOffset.intValue(), endCharacterOffset.intValue());
    }

    // --- Added for bidirectional chunk management ---
    public void addChunk(Chunk chunk) {
        if (chunk == null) return;
        if (chunks == null) chunks = new ArrayList<>();
        if (!chunks.contains(chunk)) {
            chunks.add(chunk);
            chunk.setScene(this);
        }
    }

    public void removeChunk(Chunk chunk) {
        if (chunk == null || chunks == null) return;
        if (chunks.remove(chunk)) {
            chunk.setScene(null);
        }
    }

    // --- Current event-carrier compatibility methods ---
    public java.util.UUID getEventId() {
        return id;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public Integer getSceneIndex() {
        return sceneIndex;
    }

    public Long getStartOffset() {
        return startCharacterOffset;
    }

    public Long getEndOffset() {
        return endCharacterOffset;
    }

    /**
     * Business exception for scene-detection stage failures that should preserve
     * structured ingestion failure semantics.
     */
    public static class SceneDetectionException extends RuntimeException implements IngestionFailureCarrier {

        private final IngestionFailure failure;

        public SceneDetectionException(IngestionFailure failure) {
            super(failure != null ? failure.message() : "Scene detection failed");
            this.failure = failure;
        }

        public SceneDetectionException(IngestionFailure failure, Throwable cause) {
            super(failure != null ? failure.message() : "Scene detection failed", cause);
            this.failure = failure;
        }

        public IngestionFailure failure() {
            return failure;
        }
    }

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
    public static class SceneDetectionHandler implements SceneDetectionOperation {

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
                List<Scene> scenes = detectAndPersistScenes(ctx, jobId, chapter);

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
                    Map<String, UUID> individualIds = individualPersistenceService.persistExtractedIndividuals(ctx, scenes, sceneRelationshipOutcome.sceneIndividualExtractions());
                    Map<String, UUID> collectiveIds = collectivePersistenceService.persistExtractedCollectives(ctx, scenes, sceneRelationshipOutcome.sceneCollectiveExtractions());
                    Map<String, UUID> objectIds     = objectPersistenceService.persistExtractedObjects(ctx, scenes, sceneRelationshipOutcome.sceneObjectExtractions());
                    Map<String, UUID> locationIds   = locationPersistenceService.persistExtractedLocations(ctx, scenes, sceneRelationshipOutcome.sceneLocationExtractions());
                    Map<String, UUID> eventIds      = eventPersistenceService.persistExtractedEvents(ctx, scenes, sceneRelationshipOutcome.sceneEventExtractions());
                    relationClaimPersistenceService.persistExtractedRelationClaims(
                            ctx, scenes, sceneRelationshipOutcome.sceneRelationClaimExtractions(),
                            bookId, individualIds, collectiveIds, objectIds, locationIds, eventIds
                    );
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

        private List<Scene> detectAndPersistScenes(StageExecutionContext ctx, UUID jobId, Chapter chapter) {
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
            return sceneProcessingService.persistDetectedScenes(ctx, chapterId, scenesWithCoords);
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
            // Triad analysis failures — LLM response quality issues, transient
            if (e instanceof TriadAnalysisException triadException
                    && triadException.failure() != null) {
                return true;
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

    /**
     * Synchronous operation interface for scene detection.
     *
     * <p>Implemented by {@link SceneDetectionHandler} so that the step-by-step execution controller
     * can invoke scene detection directly without going through Spring
     * {@code @TransactionalEventListener} dispatch.
     *
     * <p>The step-by-step execution controller provides the transaction context; this interface
     * simply exposes the business logic.
     */
    public static interface SceneDetectionOperation extends StageOperation {

        /**
         * Execute scene detection for a chapter within an existing transaction.
         *
         * @param jobId     the ingestion job ID (created by {@code prepare})
         * @param chapterId the chapter to process
         * @return result summarising what happened
         */
        default StepResult execute(UUID jobId, UUID chapterId) {
            return execute(new StageExecutionContext(null, jobId, chapterId, null, StageKey.SCENE_SEGMENTATION));
        }
    }

    /**
     * Data transfer object representing the result of AI scene detection.
     * Maps to the XML structure returned by the AI model.
     *
     * @param sceneIndex The 0-based index of the scene within the chapter
     * @param startAnchor Text fragment marking the beginning of the scene
     * @param contextSummary Brief description of what happens in this scene
     * @param breakReason Why the AI determined this is a scene boundary
     * @param chronology Temporal relationship to the previous scene using Allen's Interval Algebra
     * @param chronologyCertainty Level of certainty about the temporal relationship
     * @param chronologyMarker Text evidence that supports the temporal relationship
     */
    public static record SceneDetectionResult(
        int sceneIndex,
        String startAnchor,
        String contextSummary,
        String breakReason,
        String chronology,
        String chronologyCertainty,
        String chronologyMarker
    ) {}

    /**
     * AI-backed scene detection as part of the ingestion scene stage.
     */
    @Service
    @Slf4j
    @RequiredArgsConstructor
    public static class SceneDetectionService {

        public record SceneSegmentationOutcome(List<SceneWithCoordinates> scenes) {}

        private final LlmClient llmClient;
        private final SceneProcessingService sceneProcessingService;
        private final LlmRetryStrategy llmRetryStrategy;

        public SceneSegmentationOutcome detectScenesInText(UUID jobId, UUID chapterId, String chapterText) {
            if (chapterText == null || chapterText.trim().isEmpty()) {
                log.warn("Chapter {} has no text content for scene detection", chapterId);
                return new SceneSegmentationOutcome(Collections.emptyList());
            }

            log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)",
                    chapterId, jobId, chapterText.length());

            try {
                return detectScenesWithRetry(jobId, chapterId, chapterText, null);
            } catch (SceneLocalizationException e) {
                log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
                throw e;
            } catch (SceneDetectionException e) {
                log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
                throw e;
            } catch (Exception e) {
                if (isExpectedRetryableSegmentationFailure(e)) {
                    log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
                } else {
                    log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
                }
                throw buildSceneDetectionFailure(
                        "SCENE_DETECTION_FAILED",
                        "Scene detection failed: " + safeMessage((Exception) e),
                        chapterId,
                        e
                );
            }
        }

        public SceneSegmentationOutcome detectScenesInChapter(UUID jobId, Chapter chapter) {
            if (chapter == null) {
                throw new IllegalArgumentException("chapter must not be null");
            }

            UUID chapterId = chapter.getId();
            String chapterText = chapter.getRawText();
            if (chapterText == null || chapterText.trim().isEmpty()) {
                log.warn("Chapter {} has no text content for scene detection", chapterId);
                return new SceneSegmentationOutcome(Collections.emptyList());
            }

            log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)",
                    chapterId, jobId, chapterText.length());

            try {
                return detectScenesWithRetry(jobId, chapterId, chapterText, chapter);
            } catch (SceneLocalizationException e) {
                log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
                throw e;
            } catch (SceneDetectionException e) {
                log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
                throw e;
            } catch (Exception e) {
                if (isExpectedRetryableSegmentationFailure(e)) {
                    log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
                } else {
                    log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
                }
                throw buildSceneDetectionFailure(
                        "SCENE_DETECTION_FAILED",
                        "Scene detection failed: " + safeMessage((Exception) e),
                        chapterId,
                        e
                );
            }
        }

        private SceneSegmentationOutcome detectScenesWithRetry(UUID jobId,
                                                               UUID chapterId,
                                                               String chapterText,
                                                               Chapter chapter) {
            int maxAttempts = 4;
            long startTime = System.currentTimeMillis();
            Exception lastException = null;

            log.info("Chapter segmentation starting with retry (max {} attempts) for job {}",
                    maxAttempts, jobId);

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                double temperature = 0.1 + ((attempt - 1) * 0.2); // 0.1, 0.3, 0.5, 0.7
                try {
                    log.info("Chapter segmentation: attempt {}/{} with temperature={} for job {}",
                            attempt, maxAttempts, temperature, jobId);

                    SceneSegmentationOutcome result = performFullSceneDetection(
                            jobId, chapterId, chapterText, chapter, temperature);

                    long totalDuration = System.currentTimeMillis() - startTime;
                    log.info("Scene detection successful for chapter {}: attempt {}/{} in {} ms",
                            chapterId, attempt, maxAttempts, totalDuration);
                    return result;

                } catch (Exception e) {
                    lastException = e;
                    boolean retryable = isExpectedRetryableSegmentationFailure(e);
                    log.warn("[LLM-Retry] Scene Detection attempt {}/{} failed: {} (retryable={})",
                            attempt, maxAttempts, e.getMessage(), retryable);

                    if (!retryable || attempt >= maxAttempts) {
                        break;
                    }
                    // exponential backoff with jitter
                    try {
                        long delay = (long) (200 * Math.pow(2, attempt - 1));
                        Thread.sleep(delay + (long) (Math.random() * delay));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            log.error("Scene detection failed permanently after {} attempts in {} ms: {}",
                    maxAttempts, totalDuration, lastException != null ? lastException.getMessage() : "unknown");

            if (lastException instanceof SceneLocalizationException sle) {
                throw sle;
            }
            throw buildSceneDetectionFailure(
                    "SCENE_DETECTION_RETRY_EXHAUSTED",
                    "Scene detection failed with retry: " + (lastException != null ? lastException.getMessage() : "unknown"),
                    chapterId,
                    lastException
            );
        }

        private SceneSegmentationOutcome performFullSceneDetection(UUID jobId,
                                                                   UUID chapterId,
                                                                   String chapterText,
                                                                   Chapter chapterMetadata,
                                                                   double temperature) {
            try {
                log.info("Chapter segmentation: starting for job {} chapter {}", jobId, chapterId);

                LlmClient.SegmentationBudgetCheck budgetCheck = llmClient.evaluateSegmentationBudget(chapterText);
                List<SegmentWindow> segments = createDeterministicSegments(
                        chapterText,
                        budgetCheck.estimatedTotalInput(),
                        budgetCheck.usableInputBudget()
                );

                if (segments.size() == 1 && budgetCheck.isWithinBudget()) {
                    log.info("Segmentation budget check accepted for chapter {} (estimatedInput={}, budget={})",
                            chapterId, budgetCheck.estimatedTotalInput(), budgetCheck.usableInputBudget());
                } else {
                    log.info("Segmentation budget check exceeded for chapter {} (estimatedInput={}, budget={}). Using segmented processing with {} segment(s).",
                            chapterId, budgetCheck.estimatedTotalInput(), budgetCheck.usableInputBudget(), segments.size());
                }

                List<SceneWithCoordinates> scenes = processSegments(jobId, chapterId, segments, temperature);
                if (scenes.isEmpty()) {
                    throw buildSceneDetectionFailure(
                            "SCENE_COORDINATE_LOCALIZATION_EMPTY",
                            "Scene coordinate localization returned empty results",
                            chapterId,
                            null
                    );
                }

                log.debug("Successfully completed scene segmentation/localization pipeline: {} scenes detected",
                        scenes.size());
                return new SceneSegmentationOutcome(scenes);
            } catch (Exception e) {
                if (isExpectedRetryableSegmentationFailure(e)) {
                    log.warn("Triad-based scene detection pipeline failed: {}", e.getMessage());
                } else {
                    log.error("Triad-based scene detection pipeline failed: {}", e.getMessage(), e);
                }
                throw e;
            }
        }

        private boolean isExpectedRetryableSegmentationFailure(Throwable exception) {
            Throwable current = exception;
            while (current != null) {
                if (current instanceof SceneLocalizationException) {
                    return true;
                }
                if (current instanceof SceneDetectionException sceneDetectionException
                        && sceneDetectionException.failure() != null) {
                    String code = sceneDetectionException.failure().code();
                    if ("SCENE_DETECTION_RETRY_EXHAUSTED".equals(code)
                            || "SCENE_SEGMENT_NO_LOCALIZABLE_SCENES".equals(code)
                            || "SCENE_SEGMENTED_FALLBACK_EMPTY".equals(code)
                            || "SCENE_SEGMENTATION_XML_EMPTY".equals(code)
                            || "SCENE_COORDINATE_LOCALIZATION_EMPTY".equals(code)
                            || "SCENE_COORDINATE_LOCALIZATION_DROPPED_SCENES".equals(code)) {
                        return true;
                    }
                }
                if (current instanceof RuntimeException && isKnownRetryableMessage(current.getMessage())) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }

        private SceneDetectionException buildSceneDetectionFailure(String code,
                                                                   String message,
                                                                   UUID chapterId,
                                                                   Throwable cause) {
            IngestionFailure failure = IngestionFailure.builder(code, message)
                    .exceptionType(cause != null ? cause.getClass().getSimpleName() : null)
                    .stage("SCENE_DETECTION")
                    .detail("chapterId", chapterId)
                    .build();
            return new SceneDetectionException(failure, cause);
        }

        private boolean isKnownRetryableMessage(String message) {
            if (message == null) {
                return false;
            }
            return message.contains("Chapter segmentation parsing returned empty results")
                    || message.contains("Scene coordinate localization returned empty results")
                    || message.contains("Scene coordinate localization dropped scenes")
                    || message.contains("Segmented fallback produced no localizable scenes")
                    || message.contains("produced no localizable scenes")
                    || message.contains("Scene detection failed with retry:")
                    || message.contains("Chapter segmentation failed after");
        }

        private List<SceneWithCoordinates> processSegments(UUID jobId, UUID chapterId, List<SegmentWindow> segments, double temperature) {
            List<SceneWithCoordinates> rebasedScenes = new ArrayList<>();

            for (SegmentWindow segment : segments) {
                List<SceneWithCoordinates> localizedSegmentScenes = detectScenesInSingleSegment(jobId, chapterId, segment.text(), temperature);
                if (localizedSegmentScenes.isEmpty()) {
                    throw buildSceneDetectionFailure(
                            "SCENE_SEGMENT_NO_LOCALIZABLE_SCENES",
                            String.format(
                                    "Segment %d/%d produced no localizable scenes",
                                    segment.segmentIndex() + 1,
                                    segment.totalSegments()
                            ),
                            chapterId,
                            null
                    );
                }

                List<SceneWithCoordinates> sortedSegmentScenes = localizedSegmentScenes.stream()
                        .sorted(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset))
                        .toList();

                for (int i = 0; i < sortedSegmentScenes.size(); i++) {
                    SceneWithCoordinates localScene = sortedSegmentScenes.get(i);
                    boolean potentialSplitStart = segment.segmentIndex() > 0 && i == 0;
                    boolean potentialSplitEnd = segment.segmentIndex() < segment.totalSegments() - 1
                            && i == sortedSegmentScenes.size() - 1;

                    rebasedScenes.add(new SceneWithCoordinates(
                            0,
                            segment.startOffset() + localScene.startCharacterOffset(),
                            segment.startOffset() + localScene.endCharacterOffset(),
                            localScene.contextSummary(),
                            localScene.chronology(),
                            localScene.chronologyCertainty(),
                            localScene.chronologyMarker(),
                            potentialSplitStart,
                            potentialSplitEnd
                    ));
                }
            }

            if (rebasedScenes.isEmpty()) {
                throw buildSceneDetectionFailure(
                        "SCENE_SEGMENTED_FALLBACK_EMPTY",
                        "Segmented fallback produced no localizable scenes",
                        chapterId,
                        null
                );
            }

            List<SceneWithCoordinates> ordered = rebasedScenes.stream()
                    .sorted(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset))
                    .toList();

            List<SceneWithCoordinates> renumbered = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                SceneWithCoordinates scene = ordered.get(i);
                renumbered.add(new SceneWithCoordinates(
                        i,
                        scene.startCharacterOffset(),
                        scene.endCharacterOffset(),
                        scene.contextSummary(),
                        scene.chronology(),
                        scene.chronologyCertainty(),
                        scene.chronologyMarker(),
                        scene.potentialSplitSceneStart(),
                        scene.potentialSplitSceneEnd()
                ));
            }

            return renumbered;
        }

        private List<SceneWithCoordinates> detectScenesInSingleSegment(UUID jobId, UUID chapterId, String segmentText, double temperature) {
            String segmentationXmlResponse = llmClient.detectChapterSegmentation(jobId, segmentText, temperature);
            List<SceneDetectionResult> sceneResults = sceneProcessingService.parseSceneDetectionXml(segmentationXmlResponse, segmentText.length());
            if (sceneResults.isEmpty()) {
                throw buildSceneDetectionFailure(
                        "SCENE_SEGMENTATION_XML_EMPTY",
                        "Chapter segmentation parsing returned empty results - likely malformed XML response",
                        chapterId,
                        null
                );
            }
            List<SceneWithCoordinates> scenes = sceneProcessingService.localizeSceneCoordinates(segmentText, sceneResults);
            if (scenes.isEmpty()) {
                throw buildSceneDetectionFailure(
                        "SCENE_COORDINATE_LOCALIZATION_EMPTY",
                        "Scene coordinate localization returned empty results",
                        chapterId,
                        null
                );
            }
            validateLocalizationCoverage(chapterId, sceneResults.size(), scenes.size());
            return scenes;
        }

        private void validateLocalizationCoverage(UUID chapterId, int parsedSceneCount, int localizedSceneCount) {
            if (parsedSceneCount <= 0) {
                return;
            }

            if (localizedSceneCount != parsedSceneCount) {
                throw buildSceneDetectionFailure(
                        "SCENE_COORDINATE_LOCALIZATION_DROPPED_SCENES",
                        String.format(
                                "Scene coordinate localization dropped scenes (parsed=%d localized=%d)",
                                parsedSceneCount,
                                localizedSceneCount
                        ),
                        chapterId,
                        null
                );
            }
        }

        private List<SegmentWindow> createDeterministicSegments(String text, int estimatedTotalInput, int usableInputBudget) {
            if (usableInputBudget <= 0) {
                throw new IllegalArgumentException("usableInputBudget must be greater than zero");
            }

            int segmentCount = Math.max(1, (int) Math.ceil((double) estimatedTotalInput / usableInputBudget));
            if (segmentCount == 1) {
                return List.of(new SegmentWindow(0, text.length(), text, 0, 1));
            }

            List<SegmentWindow> segments = new ArrayList<>();
            int textLength = text.length();
            int previousCut = 0;

            for (int i = 1; i < segmentCount; i++) {
                int idealCut = (int) Math.round((double) textLength * i / segmentCount);
                int cut = findPreferredBoundary(text, previousCut, textLength, idealCut);
                if (cut <= previousCut || cut >= textLength) {
                    cut = Math.min(textLength - 1, Math.max(previousCut + 1, idealCut));
                }

                segments.add(new SegmentWindow(previousCut, cut, text.substring(previousCut, cut), segments.size(), segmentCount));
                previousCut = cut;
            }

            if (previousCut < textLength) {
                segments.add(new SegmentWindow(previousCut, textLength, text.substring(previousCut), segments.size(), segmentCount));
            }

            return segments;
        }

        private int findPreferredBoundary(String text, int startBound, int endBound, int idealCut) {
            int window = Math.max(200, (endBound - startBound) / 8);

            int cut = findNearestPatternBoundary(text, "\n\n", startBound, endBound, idealCut, window, 2);
            if (cut != -1) return cut;

            cut = findNearestPatternBoundary(text, "\n", startBound, endBound, idealCut, window, 1);
            if (cut != -1) return cut;

            cut = findNearestSentenceBoundary(text, startBound, endBound, idealCut, window);
            if (cut != -1) return cut;

            cut = findNearestWhitespaceBoundary(text, startBound, endBound, idealCut, window);
            if (cut != -1) return cut;

            return idealCut;
        }

        private int findNearestPatternBoundary(String text, String pattern, int startBound, int endBound,
                                               int idealCut, int window, int advance) {
            int searchStart = Math.max(startBound, idealCut - window);
            int searchEnd = Math.min(endBound, idealCut + window);

            int nearest = -1;
            int bestDistance = Integer.MAX_VALUE;
            int index = text.indexOf(pattern, searchStart);
            while (index != -1 && index < searchEnd) {
                int cut = index + advance;
                if (cut > startBound && cut < endBound) {
                    int distance = Math.abs(cut - idealCut);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = cut;
                    }
                }
                index = text.indexOf(pattern, index + 1);
            }

            return nearest;
        }

        private int findNearestSentenceBoundary(String text, int startBound, int endBound, int idealCut, int window) {
            int searchStart = Math.max(startBound + 1, idealCut - window);
            int searchEnd = Math.min(endBound - 1, idealCut + window);

            int nearest = -1;
            int bestDistance = Integer.MAX_VALUE;

            for (int i = searchStart; i < searchEnd; i++) {
                char c = text.charAt(i);
                if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1))) {
                    int cut = i + 1;
                    int distance = Math.abs(cut - idealCut);
                    if (distance < bestDistance && cut > startBound && cut < endBound) {
                        bestDistance = distance;
                        nearest = cut;
                    }
                }
            }

            return nearest;
        }

        private int findNearestWhitespaceBoundary(String text, int startBound, int endBound, int idealCut, int window) {
            int searchStart = Math.max(startBound + 1, idealCut - window);
            int searchEnd = Math.min(endBound - 1, idealCut + window);

            int nearest = -1;
            int bestDistance = Integer.MAX_VALUE;

            for (int i = searchStart; i < searchEnd; i++) {
                if (Character.isWhitespace(text.charAt(i))) {
                    int distance = Math.abs(i - idealCut);
                    if (distance < bestDistance && i > startBound && i < endBound) {
                        bestDistance = distance;
                        nearest = i;
                    }
                }
            }

            return nearest;
        }

        private record SegmentWindow(int startOffset, int endOffset, String text, int segmentIndex, int totalSegments) {
        }
    }

    /**
     * Business exception for expected scene-localization failures in the scene detection pipeline.
     */
    public static class SceneLocalizationException extends RuntimeException implements IngestionFailureCarrier {

        private final IngestionFailure failure;

        public SceneLocalizationException(IngestionFailure failure) {
            super(failure != null ? failure.message() : "Scene localization failed");
            this.failure = failure;
        }

        public SceneLocalizationException(IngestionFailure failure, Throwable cause) {
            super(failure != null ? failure.message() : "Scene localization failed", cause);
            this.failure = failure;
        }

        public IngestionFailure failure() {
            return failure;
        }
    }

    /**
     * Unified service responsible for scene processing operations:
     * XML parsing, coordinate localization, and persistence.
     *
     * This service provides granular operations to support different usage patterns.
     * Note: AI scene detection is handled by SceneDetectionClient and related
     * orchestration services to avoid circular dependencies.
     */
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public static class SceneProcessingService {

        private final ChapterGraphRepository chapterRepo;
        private final SceneGraphRepository sceneRepo;

        // =============================================================================
        // HIGH-LEVEL WORKFLOW METHODS
        // =============================================================================

        /**
         * Retrieve all scenes for a chapter.
         *
         * @param chapterId The chapter ID
         * @return List of scenes for the chapter
         */
        public List<Scene> getScenesByChapterId(UUID chapterId) {
            return sceneRepo.findByChapterId(chapterId);
        }

        /**
         * Delete all scenes for a chapter.
         *
         * @param chapterId The chapter ID
         */
        @Transactional
        public void deleteScenesByChapterId(UUID chapterId) {
            log.debug("Deleting all scenes for chapter {}", chapterId);
            sceneRepo.deleteByChapterId(chapterId);
        }

        // =============================================================================
        // GRANULAR PROCESSING METHODS
        // =============================================================================

        /**
         * Persist detected scenes to the database.
         * Separated from detection to maintain proper transaction boundaries.
         *
         * @param chapterId        The UUID of the chapter
         * @param scenesWithCoords The detected scenes with coordinates
         * @return List of persisted Scene entities
         */
        @Transactional
        public List<Scene> persistDetectedScenes(StageExecutionContext ctx, UUID chapterId, List<SceneWithCoordinates> scenesWithCoords) {
            log.debug("Persisting {} scenes for chapter {}", scenesWithCoords.size(), chapterId);

            if (scenesWithCoords.isEmpty()) {
                return List.of();
            }

            // Avoid duplicate persistence if scenes already exist
            if (!sceneRepo.findByChapterId(chapterId).isEmpty()) {
                log.info("Chapter {} already has scenes; returning existing", chapterId);
            return sceneRepo.findByChapterId(chapterId);
            }

            // Fetch chapter text to extract scene content
            String chapterText = chapterRepo.findById(chapterId)
                    .map(c -> c.getRawText())
                    .orElse(null);

            final String finalChapterText = chapterText;
            List<Scene> scenes = scenesWithCoords.stream().map(s -> {
                Scene scene = new Scene();
                scene.setSceneIndex(s.sceneIndex());
                scene.setStartCharacterOffset(s.startCharacterOffset());
                scene.setEndCharacterOffset(s.endCharacterOffset());
                scene.setContextSummary(s.contextSummary());
                scene.setChronology(s.chronology());
                scene.setChronologyCertainty(s.chronologyCertainty());
                scene.setChronologyMarker(s.chronologyMarker());

                LinkedHashSet<String> labels = new LinkedHashSet<>();
                labels.add(EVENT_LABEL);
                if (s.potentialSplitSceneStart()) {
                    labels.add(POTENTIAL_SPLIT_SCENE_START_LABEL);
                }
                if (s.potentialSplitSceneEnd()) {
                    labels.add(POTENTIAL_SPLIT_SCENE_END_LABEL);
                }
                scene.setLabels(new ArrayList<>(labels));

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

            List<Scene> toSave = scenes.stream()
                    .peek(s -> {
                        if (s.getId() == null) {
                            s.setId(UUID.randomUUID());
                        }
                        if (s.getChapterId() == null) {
                            s.setChapterId(chapterId);
                        }
                    })
                    .collect(Collectors.toList());
            toSave.forEach(scene -> scene.setStageId(ctx.stageId()));
            List<Scene> savedScenes = sceneRepo.saveAll(toSave);
            for (Scene savedScene : savedScenes) {
                if (savedScene.getId() != null) {
                    sceneRepo.linkSceneToChapter(chapterId, savedScene.getId());
                }
            }
            return savedScenes;
        }

        /**
         * Parse XML response from AI scene detection into SceneDetectionResult objects.
         * Handles markdown fencing, CDATA sections, and malformed XML gracefully.
         *
         * @param xmlResponse       Raw XML response from AI model
         * @param chapterTextLength Length of the chapter text (for validation)
         * @return List of parsed scene detection results
         */
        public List<SceneDetectionResult> parseSceneDetectionXml(String xmlResponse, int chapterTextLength) {
            if (xmlResponse == null || xmlResponse.isBlank()) {
                log.warn("Scene detection XML response is empty; returning no parsed scenes");
                return List.of();
            }

            try {
                log.trace("Parsing XML response of length: {}", xmlResponse.length());

                String cleanXml = cleanupXmlResponse(xmlResponse);
                log.trace("Full cleaned XML response: {}", cleanXml);

                if (!isValidXmlStructure(cleanXml)) {
                    return List.of();
                }

                Document document = parseXmlDocument(cleanXml);
                if (document == null) {
                    return List.of();
                }

                List<SceneDetectionResult> results = extractSceneResults(document);

                log.info("Successfully parsed {} scene detection results", results.size());
                return results;

            } catch (ParserConfigurationException | SAXException | IOException e) {
                log.error("Failed to parse scene detection XML response: {}", e.getMessage());
                log.debug("Raw response was: {}", xmlResponse);

                log.warn("Parsing failed, returning empty results for manual handling");
                return List.of();
            }
        }

        /**
         * Convert AI-identified anchors into precise character coordinates.
         * Implements sophisticated coordinate localization with fallback strategies.
         *
         * @param chapterText The full chapter text to search within
         * @param aiResults   Scene detection results with start anchors
         * @return List of scenes with calculated character coordinates, sorted by
         *         position
         */
        public List<SceneWithCoordinates> localizeSceneCoordinates(String chapterText,
                List<SceneDetectionResult> aiResults) {
            List<SceneWithCoordinates> coordinatedScenes = new ArrayList<>();

            log.debug("Localizing coordinates for {} scene results in text of length {}",
                    aiResults.size(), chapterText.length());

            List<SceneDetectionResult> sortedResults = aiResults.stream()
                    .sorted(Comparator.comparingInt(SceneDetectionResult ::sceneIndex))
                    .toList();

            for (int i = 0; i < sortedResults.size(); i++) {
                SceneDetectionResult result = sortedResults.get(i);
                try {
                    log.debug("Processing scene {}: startAnchor='{}'",
                            result.sceneIndex(),
                            result.startAnchor().length() > 20 ? result.startAnchor().substring(0, 20) + "..."
                                    : result.startAnchor());

                    long afterPosition = (i > 0 && !coordinatedScenes.isEmpty())
                            ? coordinatedScenes.get(coordinatedScenes.size() - 1).endCharacterOffset()
                            : -1;

                    long beforePosition = findNextAnchorBound(chapterText, sortedResults, i + 1, afterPosition);

                    long startPos = findAnchorPositionWithFallbacks(chapterText, result.startAnchor(), true, afterPosition,
                            beforePosition);

                    if (startPos == -1) {
                        throw sceneAnchorMismatch(result.sceneIndex(), result.startAnchor());
                    }

                    long endPos;
                    if (beforePosition != -1) {
                        endPos = beforePosition;
                        log.debug("Scene {} end position set to next anchor at: {}", result.sceneIndex(), endPos);
                    } else {
                        endPos = chapterText.length();
                        log.debug("Scene {} extended to end of chapter (no subsequent anchors found)", result.sceneIndex());
                    }

                    if (startPos < endPos) {
                        coordinatedScenes.add(new SceneWithCoordinates(
                                result.sceneIndex(),
                                startPos,
                                endPos,
                                result.contextSummary(),
                                result.chronology(),
                                result.chronologyCertainty(),
                                result.chronologyMarker(),
                                false,
                                false));
                        log.debug("Localized scene {}: start={}, end={}, length={}",
                                result.sceneIndex(), startPos, endPos, endPos - startPos);
                    } else {
                        throw sceneLocalizationFailure(
                                "SCENE_LOCALIZATION_INVALID_BOUNDS",
                                String.format(
                                        "Failed to localize scene %d: invalid bounds startPos=%d, endPos=%d",
                                        result.sceneIndex(),
                                        startPos,
                                        endPos
                                ),
                                result.sceneIndex(),
                                result.startAnchor(),
                                null
                        );
                    }
                } catch (SceneLocalizationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw sceneLocalizationFailure(
                            "SCENE_LOCALIZATION_FAILED",
                            String.format(
                                    "Error localizing scene %d: %s",
                                    result.sceneIndex(),
                                    e.getMessage()
                            ),
                            result.sceneIndex(),
                            result.startAnchor(),
                            e
                    );
                }
            }

            coordinatedScenes.sort(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset));

            log.debug("Successfully localized {} out of {} scenes", coordinatedScenes.size(), aiResults.size());

            return coordinatedScenes;
        }

        private SceneLocalizationException sceneAnchorMismatch(int sceneIndex, String startAnchor) {
            return sceneLocalizationFailure(
                    "SCENE_LOCALIZATION_ANCHOR_NOT_FOUND",
                    String.format(
                            "Failed to localize scene %d because start anchor '%s' was not found",
                            sceneIndex,
                            startAnchor
                    ),
                    sceneIndex,
                    startAnchor,
                    null
            );
        }

        private SceneLocalizationException sceneLocalizationFailure(
                String code,
                String message,
                int sceneIndex,
                String startAnchor,
                Throwable cause
        ) {
            IngestionFailure failure = IngestionFailure.builder(code, message)
                    .exceptionType(SceneLocalizationException.class.getSimpleName())
                    .stage("SCENE_SEGMENTATION")
                    .detail("sceneIndex", sceneIndex)
                    .detail("startAnchor", anchorPreview(startAnchor))
                    .build();
            return cause == null ? new SceneLocalizationException(failure) : new SceneLocalizationException(failure, cause);
        }

        private String anchorPreview(String anchor) {
            if (anchor == null) {
                return null;
            }
            return anchor.length() <= 160 ? anchor : anchor.substring(0, 157) + "...";
        }

        // =============================================================================
        // PRIVATE XML PARSING METHODS
        // =============================================================================

        private String cleanupXmlResponse(String xmlResponse) {
            String cleanXml = xmlResponse.trim();
            if (cleanXml.startsWith("```xml")) {
                cleanXml = cleanXml.substring(6);
            }
            if (cleanXml.startsWith("```")) {
                cleanXml = cleanXml.substring(3);
            }
            if (cleanXml.endsWith("```")) {
                cleanXml = cleanXml.substring(0, cleanXml.length() - 3);
            }
            return cleanXml.trim();
        }

        private boolean isValidXmlStructure(String cleanXml) {
            if (!cleanXml.trim().startsWith("<")) {
                log.error("XML does not start with '<' character. First 50 chars: '{}'",
                        cleanXml.substring(0, Math.min(50, cleanXml.length())));
                return false;
            }
            return true;
        }

        private Document parseXmlDocument(String cleanXml) throws ParserConfigurationException, SAXException, IOException {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            log.debug("About to parse XML of length: {}", cleanXml.length());
            log.debug("XML starts with: '{}'", cleanXml.substring(0, Math.min(100, cleanXml.length())));

            byte[] xmlBytes = cleanXml.getBytes(StandardCharsets.UTF_8);
            log.debug("XML byte array length: {}, first 10 bytes: {}", xmlBytes.length,
                    Arrays.toString(Arrays.copyOf(xmlBytes, Math.min(10, xmlBytes.length))));

            Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
            log.debug("XML parsing completed successfully, document is not null: {}", document != null);

            if (document == null) {
                log.error("Document is null after parsing!");
                return null;
            }

            document.getDocumentElement().normalize();
            log.debug("Document normalized, getting root element...");

            Element rootElement = document.getDocumentElement();
            if (rootElement == null) {
                log.error("Root element is null!");
                return null;
            }

            log.debug("Root element name: '{}'", rootElement.getNodeName());
            return document;
        }

        private List<SceneDetectionResult> extractSceneResults(Document document) {
            List<SceneDetectionResult> results = new ArrayList<>();
            Element rootElement = document.getDocumentElement();

            NodeList sceneNodes = document.getElementsByTagName("scene");
            log.debug("Found {} scene nodes in document", sceneNodes.getLength());

            if (sceneNodes.getLength() == 0) {
                logMissingSceneNodes(rootElement);
                return results;
            }

            for (int i = 0; i < sceneNodes.getLength(); i++) {
                org.w3c.dom.Node sceneNode = sceneNodes.item(i);

                if (sceneNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element sceneElement = (Element) sceneNode;
                    int sceneIndex = getIntValue(sceneElement, "index");
                    if (sceneIndex < 0) {
                        sceneIndex = i;
                    }
                    String startAnchor = getStringValue(sceneElement, "start_anchor");
                    String contextSummary = getStringValue(sceneElement, "context_summary");
                    String breakReason = getStringValue(sceneElement, "break_reason");
                    String chronology = getStringValue(sceneElement, "chronology");
                    String chronologyCertainty = getStringValue(sceneElement, "chronology_certainty");
                    String chronologyMarker = getStringValue(sceneElement, "chronology_marker");

                    if (startAnchor != null && contextSummary != null) {
                        results.add(new SceneDetectionResult(
                                sceneIndex,
                                startAnchor,
                                contextSummary,
                                breakReason,
                                chronology,
                                chronologyCertainty,
                                chronologyMarker));
                    } else {
                        log.warn("Skipping incomplete scene: index={}, start={}, context={}, reason={}",
                                sceneIndex,
                                startAnchor != null ? "present" : "missing",
                                contextSummary != null ? "present" : "missing",
                                breakReason != null ? "present" : "missing");
                    }
                }
            }

            return results;
        }

        private void logMissingSceneNodes(Element rootElement) {
            log.warn("No scene nodes found! Root element children:");
            NodeList rootChildren = rootElement.getChildNodes();
            for (int j = 0; j < rootChildren.getLength(); j++) {
                org.w3c.dom.Node child = rootChildren.item(j);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    log.warn("  Child element: '{}'", child.getNodeName());
                }
            }
        }

        private int getIntValue(Element parentElement, String tagName) {
            NodeList nodeList = parentElement.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                String textContent = nodeList.item(0).getTextContent().trim();
                try {
                    return Integer.parseInt(textContent);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse integer value from element '{}': '{}'", tagName, textContent);
                    return 0;
                }
            }
            return 0;
        }

        private String getStringValue(Element parentElement, String tagName) {
            NodeList nodeList = parentElement.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                org.w3c.dom.Node node = nodeList.item(0);
                String textContent = node.getTextContent();
                return textContent != null ? textContent.trim() : null;
            }
            return null;
        }

        // =============================================================================
        // PRIVATE COORDINATE LOCALIZATION METHODS
        // =============================================================================

        // Constants for coordinate localization
        private static final int MIN_WORDS_BEFORE_FUZZY = 5;
        private static final int MAX_LEVENSHTEIN_DISTANCE = 3;
        private static final double FUZZY_SIMILARITY_THRESHOLD = 0.85;

        private long findNextAnchorBound(String chapterText, List<SceneDetectionResult> sortedResults, int startIndex,
                                         long afterPosition) {
            for (int j = startIndex; j < sortedResults.size(); j++) {
                SceneDetectionResult futureResult = sortedResults.get(j);
                long nextPos = findAnchorPositionWithFallbacks(chapterText, futureResult.startAnchor(), true, afterPosition,
                        -1);
                if (nextPos != -1) {
                    log.debug("Found next boundary anchor for scene {} at position {} (from scene {})",
                            startIndex - 1, nextPos, j);
                    return nextPos;
                } else {
                    log.debug("Scene {} anchor not found, looking further ahead...", j);
                }
            }

            log.debug("No subsequent anchor found for boundary - will extend to chapter end");
            return -1;
        }

        private long findAnchorPositionWithFallbacks(String chapterText, String anchor, boolean isStart, long afterPosition,
                long beforePosition) {
            if (anchor == null || anchor.trim().isEmpty()) {
                log.debug("Empty anchor provided for {} position", isStart ? "start" : "end");
                return -1;
            }

            String normalizedAnchor = anchor.trim();

            log.debug("Searching for {} anchor: [{}] in text of length {} (after: {}, before: {})",
                    isStart ? "start" : "end", normalizedAnchor, chapterText.length(), afterPosition, beforePosition);

            // Tier 1: Exact match
            long position = findExactMatch(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
            if (position != -1) {
                log.debug("Found exact match for anchor '{}' at position {}",
                        normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor,
                        position);
                return position;
            }

            // Tier 2: Word trimming
            position = findWithWordTrimming(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
            if (position != -1) {
                log.info("Found anchor using word trimming for '{}' at position {}",
                        normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor,
                        position);
                return position;
            }

            // Tier 3: Fuzzy matching
            position = findWithFuzzyMatching(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
            if (position != -1) {
                log.info("Found anchor using fuzzy matching for '{}' at position {}",
                        normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor,
                        position);
                return position;
            }

            log.warn("All fallback methods failed for anchor: '{}'",
                    normalizedAnchor.length() > 40 ? normalizedAnchor.substring(0, 40) + "..." : normalizedAnchor);
            return -1;
        }

        private long findExactMatch(String chapterText, String anchor, boolean isStart, long afterPosition,
                long beforePosition) {
            int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
            int searchEnd = (beforePosition == -1) ? chapterText.length() : (int) beforePosition;

            if (searchStart >= searchEnd) {
                return -1;
            }

            String searchArea = chapterText.substring(searchStart, searchEnd);

            int pos = isStart ? searchArea.indexOf(anchor) : searchArea.lastIndexOf(anchor);

            if (pos == -1) {
                String normalizedAnchor = normalizeWhitespaceForComparison(anchor);
                String normalizedSearchArea = normalizeWhitespaceForComparison(searchArea);

                int normalizedPos = isStart ? normalizedSearchArea.indexOf(normalizedAnchor)
                        : normalizedSearchArea.lastIndexOf(normalizedAnchor);

                if (normalizedPos != -1) {
                    pos = mapNormalizedPositionToOriginal(searchArea, normalizedSearchArea, normalizedPos);
                    log.debug("Found match using whitespace normalization at position {}", pos);
                }
            }

            if (pos == -1) {
                return -1;
            }

            int actualPos = searchStart + pos;
            return isStart ? actualPos : actualPos + anchor.length();
        }

        private long findWithWordTrimming(String chapterText, String anchor, boolean isStart, long afterPosition,
                long beforePosition) {
            String[] words = anchor.split("\\s+");

            if (words.length < MIN_WORDS_BEFORE_FUZZY) {
                log.debug("Anchor too short for word trimming: {} words", words.length);
                return -1;
            }

            int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
            int searchEnd = (beforePosition == -1) ? chapterText.length() : (int) beforePosition;

            if (searchStart >= searchEnd) {
                log.debug("Invalid search bounds for word trimming: start={}, end={}", searchStart, searchEnd);
                return -1;
            }

            String searchArea = chapterText.substring(searchStart, searchEnd);

            for (int wordCount = words.length - 1; wordCount >= MIN_WORDS_BEFORE_FUZZY; wordCount--) {
                String trimmedAnchor = String.join(" ", Arrays.copyOf(words, wordCount));

                List<Integer> matches = findAllMatchesInBounds(searchArea, trimmedAnchor, 0, searchArea.length());

                if (matches.isEmpty()) {
                    String normalizedAnchor = normalizeWhitespaceForComparison(trimmedAnchor);
                    String normalizedSearchArea = normalizeWhitespaceForComparison(searchArea);
                    matches = findAllNormalizedMatchesInBounds(searchArea, normalizedSearchArea, normalizedAnchor, 0,
                            searchArea.length());
                }

                if (matches.size() == 1) {
                    int pos = matches.get(0);
                    log.debug("Found unique bounded match using {} words: '{}' at position {}",
                            wordCount, trimmedAnchor.length() > 30 ? trimmedAnchor.substring(0, 30) + "..." : trimmedAnchor,
                            pos);
                    int actualPos = searchStart + pos;
                    return isStart ? actualPos : actualPos + trimmedAnchor.length();
                } else if (matches.isEmpty()) {
                    log.debug("No bounded match found for trimmed anchor: '{}'", trimmedAnchor);
                    continue;
                } else {
                    log.debug("Multiple bounded matches found for trimmed anchor '{}': {} occurrences within bounds",
                            trimmedAnchor, matches.size());
                    continue;
                }
            }

            log.debug("Word trimming failed - no unique bounded match found");
            return -1;
        }

        private long findWithFuzzyMatching(String chapterText, String anchor, boolean isStart, long afterPosition,
                long beforePosition) {
            int anchorLength = anchor.length();
            int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
            int searchEnd = (beforePosition == -1) ? chapterText.length() - anchorLength
                    : (int) beforePosition - anchorLength;

            if (searchStart >= searchEnd) {
                return -1;
            }

            int bestPosition = -1;
            int bestDistance = Integer.MAX_VALUE;

            for (int i = searchStart; i <= searchEnd; i++) {
                String candidate = chapterText.substring(i, i + anchorLength);
                int distance = levenshteinDistance(anchor, candidate);

                if (distance <= MAX_LEVENSHTEIN_DISTANCE && distance < bestDistance) {
                    double similarity = 1.0 - (double) distance / Math.max(anchor.length(), candidate.length());
                    if (similarity >= FUZZY_SIMILARITY_THRESHOLD) {
                        bestDistance = distance;
                        bestPosition = i;
                    }
                }
            }

            if (bestPosition != -1) {
                log.debug("Found bounded fuzzy match with distance {} at position {}", bestDistance, bestPosition);
                return isStart ? bestPosition : bestPosition + anchorLength;
            }

            log.debug("No suitable bounded fuzzy match found within threshold");
            return -1;
        }

        private List<Integer> findAllMatchesInBounds(String text, String substring, int searchStart, int searchEnd) {
            List<Integer> matches = new ArrayList<>();

            if (searchStart >= searchEnd || searchStart < 0 || searchEnd > text.length()) {
                return matches;
            }

            String searchArea = text.substring(searchStart, searchEnd);
            int index = searchArea.indexOf(substring);

            while (index != -1 && searchStart + index + substring.length() <= searchEnd) {
                matches.add(searchStart + index);
                index = searchArea.indexOf(substring, index + 1);
            }

            return matches;
        }

        private List<Integer> findAllNormalizedMatchesInBounds(String originalText, String normalizedText,
                String normalizedSubstring, int searchStart, int searchEnd) {
            List<Integer> matches = new ArrayList<>();

            int index = normalizedText.indexOf(normalizedSubstring);

            while (index != -1) {
                int originalPos = mapNormalizedPositionToOriginal(originalText, normalizedText, index);

                if (originalPos >= searchStart && originalPos < searchEnd) {
                    matches.add(originalPos);
                }

                index = normalizedText.indexOf(normalizedSubstring, index + 1);
            }

            return matches;
        }

        private int levenshteinDistance(String s1, String s2) {
            int len1 = s1.length();
            int len2 = s2.length();

            int[][] dp = new int[len1 + 1][len2 + 1];

            for (int i = 0; i <= len1; i++) {
                dp[i][0] = i;
            }
            for (int j = 0; j <= len2; j++) {
                dp[0][j] = j;
            }

            for (int i = 1; i <= len1; i++) {
                for (int j = 1; j <= len2; j++) {
                    if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                        dp[i][j] = dp[i - 1][j - 1];
                    } else {
                        dp[i][j] = Math.min(
                                dp[i - 1][j] + 1,
                                Math.min(
                                        dp[i][j - 1] + 1,
                                        dp[i - 1][j - 1] + 1));
                    }
                }
            }

            return dp[len1][len2];
        }

        private String normalizeWhitespaceForComparison(String text) {
            if (text == null)
                return null;
            return text.replaceAll("\\s+", " ").trim();
        }

        private int mapNormalizedPositionToOriginal(String originalText, String normalizedText, int normalizedPos) {
            if (normalizedPos == 0) {
                for (int i = 0; i < originalText.length(); i++) {
                    if (!Character.isWhitespace(originalText.charAt(i))) {
                        return i;
                    }
                }
                return 0;
            }

            int originalPos = 0;
            int normalizedCount = 0;
            boolean inWhitespace = false;

            for (int i = 0; i < originalText.length() && normalizedCount < normalizedPos; i++) {
                char c = originalText.charAt(i);

                if (Character.isWhitespace(c)) {
                    if (!inWhitespace) {
                        normalizedCount++;
                        inWhitespace = true;
                    }
                } else {
                    normalizedCount++;
                    inWhitespace = false;
                }

                originalPos = i;

                if (normalizedCount >= normalizedPos) {
                    return inWhitespace ? i : i + 1;
                }
            }

            return originalPos;
        }
    }

    /**
     * Data transfer object representing a scene with calculated character coordinates.
     * Result of the coordinate localization phase where AI-identified anchors
     * are converted to precise character offsets within the chapter text.
     *
     * @param sceneIndex The 0-based index of the scene within the chapter
     * @param startCharacterOffset Character offset where the scene begins (inclusive)
     * @param endCharacterOffset Character offset where the scene ends (exclusive)
     * @param contextSummary Brief description of what happens in this scene
     * @param chronology Temporal relationship hint extracted during scene analysis
     * @param chronologyCertainty Certainty level for chronology hint
     * @param chronologyMarker Text marker supporting chronology hint
     * @param potentialSplitSceneStart Whether this scene may be a split fragment start
     * @param potentialSplitSceneEnd Whether this scene may be a split fragment end
     */
    public static record SceneWithCoordinates(
        int sceneIndex,
        long startCharacterOffset,
        long endCharacterOffset,
        String contextSummary,
        String chronology,
        String chronologyCertainty,
        String chronologyMarker,
        boolean potentialSplitSceneStart,
        boolean potentialSplitSceneEnd
    ) {
        public SceneWithCoordinates(int sceneIndex, long startCharacterOffset, long endCharacterOffset, String contextSummary) {
            this(sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary, null, null, null, false, false);
        }

        public SceneWithCoordinates(int sceneIndex,
                                    long startCharacterOffset,
                                    long endCharacterOffset,
                                    String contextSummary,
                                    boolean potentialSplitSceneStart,
                                    boolean potentialSplitSceneEnd) {
            this(sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary, null, null, null,
                    potentialSplitSceneStart, potentialSplitSceneEnd);
        }
    }
}
