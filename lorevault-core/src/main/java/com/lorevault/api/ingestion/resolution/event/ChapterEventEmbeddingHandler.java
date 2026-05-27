package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/**
 * Stage 4 handler for ChapterEvent embedding and in-memory ANN candidate generation.
 *
 * <p>Responds to {@link StageTriggeredEvent} for {@code CHAPTER_EVENT_EMBEDDING}. Embeds stale
 * {@code ChapterEvent.aggregateCard} values, reloads the chapter event set, generates deduplicated
 * ANN candidate pairs, runs semantic merge verification, and persists book-level events.
 * Emits {@link StageCompletedEvent} so the DAG coordinator can evaluate downstream transitions.
 */
@Component
public class ChapterEventEmbeddingHandler {

    private static final Logger log = LoggerFactory.getLogger(ChapterEventEmbeddingHandler.class);

    private final ChapterEventEmbeddingService embeddingService;
    private final ChapterEventEmbeddingTransactionSupport txSupport;
    private final BookEventAnnCandidateService annCandidateService;
    private final BookEventMergeVerificationService mergeVerificationService;
    private final BookEventReductionService bookEventReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public ChapterEventEmbeddingHandler(
            ChapterEventEmbeddingService embeddingService,
            ChapterEventEmbeddingTransactionSupport txSupport,
            BookEventAnnCandidateService annCandidateService,
            BookEventMergeVerificationService mergeVerificationService,
            BookEventReductionService bookEventReductionService,
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.embeddingService = embeddingService;
        this.txSupport = txSupport;
        this.annCandidateService = annCandidateService;
        this.mergeVerificationService = mergeVerificationService;
        this.bookEventReductionService = bookEventReductionService;
        this.eventPublisher = eventPublisher;
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        // 0. Stage key guard: reject events for other stages
        if (event.getStage() != StageKey.CHAPTER_EVENT_EMBEDDING) return;

        // 1. Guard: only one thread executes at a time
        if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) {
            return;
        }

        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();

        // 2. Idempotency: does StageOutput already exist?
        if (stageOutputRepo.existsByChapterIdAndStep(chapterId, event.getStage())) {
            stageRepo.setSkipped(jobId, event.getStage());
            eventPublisher.publishEvent(new StageCompletedEvent(
                    this, jobId, chapterId, event.getStage(),
                    StepResult.success(event.getStage(),
                            "Skipped \u2014 already completed", 0L)));
            log.info("[SKIPPED] Stage {} already completed for chapter {}", event.getStage(), chapterId);
            return;
        }

        log.info("[LANE:EVENT] [EVENT_EMBEDDING] Started: jobId={}, chapterId={}",
                jobId, chapterId);

        // 3. Do the work (4 sub-stages: embedding, ANN, merge verification, book event reduction)
        StepResult result;
        long start = System.currentTimeMillis();
        try {
            int embeddedCount = embeddingService.embedChapterEvents(chapterId);
            List<ChapterEvent> chapterEvents = txSupport.loadChapterEvents(chapterId);
            List<BookEventCandidatePair> candidatePairs = annCandidateService.generateCandidates(chapterEvents, chapterId);

            Map<UUID, ChapterEvent> currentChapterEventsById = chapterEvents.stream()
                    .filter(chapterEvent -> chapterEvent != null && chapterEvent.id() != null)
                    .collect(Collectors.toMap(ChapterEvent::id, chapterEvent -> chapterEvent));

            List<UUID> candidateEndpointIds = candidatePairs.stream()
                    .filter(pair -> pair != null)
                    .flatMap(pair -> java.util.stream.Stream.of(pair.eventId1(), pair.eventId2()))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            List<ChapterEvent> candidateEndpointEvents = txSupport.loadChapterEventsByIds(candidateEndpointIds);
            Map<UUID, ChapterEvent> chapterEventsById = candidateEndpointEvents.stream()
                    .filter(chapterEvent -> chapterEvent != null && chapterEvent.id() != null)
                    .collect(Collectors.toMap(
                            ChapterEvent::id,
                            Function.identity(),
                            (left, right) -> left
                    ));
            currentChapterEventsById.forEach(chapterEventsById::putIfAbsent);

            log.info(
                    "[LANE:EVENT] [EVENT_MERGE] Starting semantic merge verification: jobId={}, chapterId={}, candidatePairCount={}",
                    jobId,
                    chapterId,
                    candidatePairs.size()
            );
            List<EventMergeModels.EventMergeVerification> verifications = mergeVerificationService.verifyCandidates(
                    jobId,
                    chapterId,
                    candidatePairs,
                    chapterEventsById
            );

            List<EventMergeModels.EventMergeDecision> mergeDecisions = verifications.stream()
                    .filter(v -> v != null && v.decision() == EventMergeModels.MergeDecision.MERGE)
                    .map(v -> new EventMergeModels.EventMergeDecision(v.eventId1(), v.eventId2(), v.confidence()))
                    .toList();

            log.info(
                    "[LANE:EVENT] [BOOK_EVENT] Starting write path: jobId={}, chapterId={}, chapterEventCount={}, mergeDecisionCount={}",
                    jobId,
                    chapterId,
                    chapterEvents.size(),
                    mergeDecisions.size()
            );
            BookEventReductionService.BookEventReductionResult reductionResult =
                    bookEventReductionService.reduceAndPersist(
                            jobId,
                            chapterId,
                            event.getBookId(),
                            List.copyOf(chapterEventsById.values()),
                            mergeDecisions
                    );

            long elapsed = System.currentTimeMillis() - start;

            log.info(
                    "[LANE:EVENT] [EVENT_EMBEDDING] Completed: jobId={}, chapterId={}, embeddedCount={}, candidatePairCount={}, bookEventsCreated={}, referenceLinksWritten={}",
                    jobId,
                    chapterId,
                    embeddedCount,
                    candidatePairs.size(),
                    reductionResult.bookEventsCreated(),
                    reductionResult.referenceLinksWritten()
            );

            result = StepResult.success(StageKey.CHAPTER_EVENT_EMBEDDING,
                    String.format("Embedded %d events, %d candidate pairs, %d book events created",
                            embeddedCount, candidatePairs.size(), reductionResult.bookEventsCreated()),
                    Map.of(
                            "embeddedCount", embeddedCount,
                            "candidatePairCount", candidatePairs.size(),
                            "bookEventsCreated", reductionResult.bookEventsCreated(),
                            "referenceLinksWritten", reductionResult.referenceLinksWritten()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:EVENT] [EVENT_EMBEDDING] Failed: jobId={}, chapterId={}: {}",
                    jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            result = retryable
                    ? StepResult.retryableFailure(StageKey.CHAPTER_EVENT_EMBEDDING,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.CHAPTER_EVENT_EMBEDDING,
                            sanitizeMessage(e), elapsed);
        }

        // 4. Emit completion — coordinator handles downstream
        eventPublisher.publishEvent(new StageCompletedEvent(
                this, jobId, chapterId, event.getStage(), result));
    }

    private boolean isRetryableError(Exception e) {
        if (e instanceof EmbeddingGenerationException embeddingGenerationException
                && embeddingGenerationException.failure() != null) {
            return "EVENT_EMBEDDING_BACKEND_UNAVAILABLE".equals(embeddingGenerationException.failure().code());
        }
        String message = e.getMessage();
        return message != null && (
                message.contains("API")
                        || message.contains("timeout")
                        || message.contains("rate limit")
                        || message.contains("connection")
        );
    }
}
