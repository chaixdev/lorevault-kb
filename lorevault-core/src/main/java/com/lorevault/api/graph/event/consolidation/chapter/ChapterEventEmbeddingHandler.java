package com.lorevault.api.graph.event.consolidation.chapter;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import static com.lorevault.api.common.ExceptionSanitizer.sanitize;

import com.lorevault.api.graph.event.consolidation.book.BookEventAnnCandidateService;
import com.lorevault.api.graph.event.consolidation.book.BookEventCandidatePair;
import com.lorevault.api.graph.event.consolidation.book.BookEventConsolidationService;
import com.lorevault.api.graph.event.consolidation.book.BookEventMergeVerificationService;
import com.lorevault.api.graph.event.persistence.BookEventGraphRepository;
import com.lorevault.api.graph.event.persistence.ChapterEventGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StageResult;
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
    private final ChapterEventGraphRepository chapterEventRepo;
    private final BookEventGraphRepository bookEventRepo;

    public ChapterEventEmbeddingHandler(
            ChapterEventEmbeddingService embeddingService,
            ChapterEventEmbeddingTransactionSupport txSupport,
            BookEventAnnCandidateService annCandidateService,
            BookEventMergeVerificationService mergeVerificationService,
            BookEventConsolidationService bookEventConsolidationService,
            ChapterEventGraphRepository chapterEventRepo,
            BookEventGraphRepository bookEventRepo
    ) {
        this.embeddingService = embeddingService;
        this.txSupport = txSupport;
        this.annCandidateService = annCandidateService;
        this.mergeVerificationService = mergeVerificationService;
        this.bookEventConsolidationService = bookEventConsolidationService;
        this.chapterEventRepo = chapterEventRepo;
        this.bookEventRepo = bookEventRepo;
    }

    @Override
    public StageResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        UUID bookId = ctx.bookId();

        long start = System.currentTimeMillis();

        // Idempotency: skip if all ChapterEvents are already embedded and a BookEvent exists
        long unembeddedCount = chapterEventRepo.countChapterEventsWithoutEmbedding(chapterId);
        long bookEventCount = bookEventRepo.countByChapterId(chapterId);
        if (unembeddedCount == 0 && bookEventCount > 0) {
            long totalCount = chapterEventRepo.countChapterEventsByChapterId(chapterId);
            log.info("[EVENT_EMBEDDING] Skipping — all {} ChapterEvents already embedded, BookEvent(s) exist for chapter {}",
                    totalCount, chapterId);
            long elapsed = System.currentTimeMillis() - start;
            return StageResult.success(StageKey.CHAPTER_EVENT_EMBEDDING,
                    "Already completed",
                    Map.of("embeddedCount", (int) totalCount,
                            "bookEventsCreated", (int) bookEventCount),
                    elapsed);
        }

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

            return StageResult.success(StageKey.CHAPTER_EVENT_EMBEDDING,
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
                    ? StageResult.retryableFailure(StageKey.CHAPTER_EVENT_EMBEDDING,
                            sanitize(e), elapsed)
                    : StageResult.failure(StageKey.CHAPTER_EVENT_EMBEDDING,
                            sanitize(e), elapsed);
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
