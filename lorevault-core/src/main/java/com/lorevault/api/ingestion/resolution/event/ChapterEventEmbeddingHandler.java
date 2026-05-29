package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 4 handler for ChapterEvent embedding and in-memory ANN candidate generation.
 *
 * <p>Responds to {@link StageTriggeredEvent} for {@code CHAPTER_EVENT_EMBEDDING}. Embeds stale
 * {@code ChapterEvent.aggregateCard} values, reloads the chapter event set, generates deduplicated
 * ANN candidate pairs, runs semantic merge verification, and persists book-level events.
 * Emits {@link StageCompletedEvent} so the DAG coordinator can evaluate downstream transitions.
 */
@Slf4j
@Component
@ForStage(StageKey.CHAPTER_EVENT_EMBEDDING)
public class ChapterEventEmbeddingHandler implements StageOperation {

    private final ChapterEventEmbeddingService embeddingService;
    private final ChapterEventEmbeddingTransactionSupport txSupport;
    private final BookEventAnnCandidateService annCandidateService;
    private final BookEventMergeVerificationService mergeVerificationService;
    private final BookEventConsolidationService bookEventConsolidationService;

    public ChapterEventEmbeddingHandler(
            ChapterEventEmbeddingService embeddingService,
            ChapterEventEmbeddingTransactionSupport txSupport,
            BookEventAnnCandidateService annCandidateService,
            BookEventMergeVerificationService mergeVerificationService,
            BookEventConsolidationService bookEventConsolidationService
    ) {
        this.embeddingService = embeddingService;
        this.txSupport = txSupport;
        this.annCandidateService = annCandidateService;
        this.mergeVerificationService = mergeVerificationService;
        this.bookEventConsolidationService = bookEventConsolidationService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        UUID bookId = ctx.bookId();

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
                    "[EVENT_MERGE] Starting semantic merge verification: jobId={}, chapterId={}, candidatePairCount={}",
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
                    "[BOOK_EVENT] Starting write path: jobId={}, chapterId={}, chapterEventCount={}, mergeDecisionCount={}",
                    jobId,
                    chapterId,
                    chapterEvents.size(),
                    mergeDecisions.size()
            );
            BookEventConsolidationService.BookEventConsolidationResult reductionResult =
                    bookEventConsolidationService.reduceAndPersist(
                            ctx,
                            jobId,
                            chapterId,
                            bookId,
                            List.copyOf(chapterEventsById.values()),
                            mergeDecisions
                    );

            long elapsed = System.currentTimeMillis() - start;

            log.info(
                    "[EVENT_EMBEDDING] Completed: jobId={}, chapterId={}, embeddedCount={}, candidatePairCount={}, bookEventsCreated={}, referenceLinksWritten={}",
                    jobId,
                    chapterId,
                    embeddedCount,
                    candidatePairs.size(),
                    reductionResult.bookEventsCreated(),
                    reductionResult.referenceLinksWritten()
            );

            return StepResult.success(StageKey.CHAPTER_EVENT_EMBEDDING,
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
            log.error("[EVENT_EMBEDDING] Failed: jobId={}, chapterId={}: {}",
                    jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.CHAPTER_EVENT_EMBEDDING,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.CHAPTER_EVENT_EMBEDDING,
                            sanitizeMessage(e), elapsed);
        }
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
