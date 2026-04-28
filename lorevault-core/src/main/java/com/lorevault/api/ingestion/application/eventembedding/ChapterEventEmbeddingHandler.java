package com.lorevault.api.ingestion.application.eventembedding;

import com.lorevault.api.content.entities.ChapterEvent;
import com.lorevault.api.ai.domain.EmbeddingGenerationException;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.events.BookEventCandidatesGeneratedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ChapterEventEmbeddingHandler {

    static final String STAGE_EVENT_EMBEDDING = "EVENT_EMBEDDING";

    private final ChapterEventEmbeddingService embeddingService;
    private final ChapterEventEmbeddingTransactionSupport txSupport;
    private final BookEventAnnCandidateService annCandidateService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChapterEventEmbeddingHandler(
            ChapterEventEmbeddingService embeddingService,
            ChapterEventEmbeddingTransactionSupport txSupport,
            BookEventAnnCandidateService annCandidateService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.embeddingService = embeddingService;
        this.txSupport = txSupport;
        this.annCandidateService = annCandidateService;
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

                    eventPublisher.publishEvent(new BookEventCandidatesGeneratedEvent(
                            this,
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            embeddedCount,
                            candidatePairs.size()
                    ));

                    log.info(
                            "[EVENT_EMBEDDING] Completed: jobId={}, correlationId={}, chapterId={}, bookId={}, embeddedCount={}, candidatePairCount={}",
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            embeddedCount,
                            candidatePairs.size()
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
