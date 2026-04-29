package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.events.BookEventCandidatesGeneratedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Stage 4 handler for ChapterEvent embedding and in-memory ANN candidate generation.
 *
 * <p>Listens after deterministic chapter event aggregation completes. The handler embeds stale
 * {@code ChapterEvent.aggregateCard} values, reloads the chapter event set, generates deduplicated
 * ANN candidate pairs, and publishes {@link BookEventCandidatesGeneratedEvent} as the fifth fan-in
 * branch for ingestion completion.
 */
@Component
public class ChapterEventEmbeddingHandler {

    private static final Logger log = LoggerFactory.getLogger(ChapterEventEmbeddingHandler.class);

    static final String STAGE_EVENT_EMBEDDING = "EVENT_EMBEDDING";

    private final ChapterEventEmbeddingService embeddingService;
    private final ChapterEventEmbeddingTransactionSupport txSupport;
    private final BookEventAnnCandidateService annCandidateService;
    private final BookEventMergeVerificationService mergeVerificationService;
    private final BookEventReductionService bookEventReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChapterEventEmbeddingHandler(
            ChapterEventEmbeddingService embeddingService,
            ChapterEventEmbeddingTransactionSupport txSupport,
            BookEventAnnCandidateService annCandidateService,
            BookEventMergeVerificationService mergeVerificationService,
            BookEventReductionService bookEventReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.embeddingService = embeddingService;
        this.txSupport = txSupport;
        this.annCandidateService = annCandidateService;
        this.mergeVerificationService = mergeVerificationService;
        this.bookEventReductionService = bookEventReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleChapterEventsResolved(ChapterEventsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();
        UUID correlationId = event.getCorrelationId();

        log.info(
                "[EVENT_EMBEDDING] Started: jobId={}, correlationId={}, chapterId={}, bookId={}, chapterEventCount={}",
                jobId,
                correlationId,
                chapterId,
                bookId,
                event.getChapterEventCount()
        );

        stageSupport.runStage(
                this,
                STAGE_EVENT_EMBEDDING,
                jobId,
                correlationId,
                chapterId,
                () -> {
                    stageSupport.updateJobStatus(
                            jobId,
                            IngestionStatus.EVENT_CANDIDATE_GENERATION,
                            "Embedding chapter events and generating ANN event candidates",
                            Map.of(
                                    "correlationId", correlationId.toString(),
                                    "chapterId", chapterId.toString(),
                                    "bookId", String.valueOf(bookId),
                                    "chapterEventCount", event.getChapterEventCount()
                            )
                    );

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
                    BookEventReductionService.BookEventReductionResult reductionResult =
                            bookEventReductionService.reduceAndPersist(
                                    jobId,
                                    chapterId,
                                    bookId,
                                    List.copyOf(chapterEventsById.values()),
                                    mergeDecisions
                            );

                    eventPublisher.publishEvent(new BookEventCandidatesGeneratedEvent(
                            this,
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            embeddedCount,
                            candidatePairs.size(),
                            reductionResult.bookEventsCreated()
                    ));

                    log.info(
                            "[EVENT_EMBEDDING] Completed: jobId={}, correlationId={}, chapterId={}, bookId={}, embeddedCount={}, candidatePairCount={}, bookEventsCreated={}, referenceLinksWritten={}",
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            embeddedCount,
                            candidatePairs.size(),
                            reductionResult.bookEventsCreated(),
                            reductionResult.referenceLinksWritten()
                    );
                    return null;
                },
                this::isRetryableError
        );
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
