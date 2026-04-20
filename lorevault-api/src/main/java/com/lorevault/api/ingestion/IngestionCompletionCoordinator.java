package com.lorevault.api.ingestion;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class IngestionCompletionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(IngestionCompletionCoordinator.class);

    private final IngestionJobGraphRepository jobRepo;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<CompletionKey, CompletionState> completionStates = new ConcurrentHashMap<>();

    public IngestionCompletionCoordinator(
            IngestionJobGraphRepository jobRepo,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jobRepo = jobRepo;
        this.ingestionJobService = ingestionJobService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleEmbeddingsCompleted(EmbeddingsCompletedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getChapterId()
        );
        logBranchArrival(
                "EMBEDDINGS_COMPLETED",
                key,
                null,
                "totalScenes=" + event.getTotalScenes()
                        + ", totalChunks=" + event.getTotalChunks()
                        + ", totalEmbeddings=" + event.getTotalEmbeddings()
                        + ", chapterLength=" + event.getChapterLength()
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.embeddingsCompletedEvent = event;
            return next;
        });

        logCoordinatorState("EMBEDDINGS_COMPLETED", key);

        completeIfReady(key);
    }

    @Async
    @EventListener
    public void handleBookIndividualsReduced(BookIndividualsReducedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getChapterId()
        );
        logBranchArrival(
                "BOOK_INDIVIDUALS_REDUCED",
                key,
                event.getBookId(),
                "processed=" + event.isProcessed()
                        + ", chapterIndividualCount=" + event.getChapterIndividualCount()
                        + ", bookIndividualCount=" + event.getBookIndividualCount()
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.bookIndividualsReducedEvent = event;
            return next;
        });

        logCoordinatorState("BOOK_INDIVIDUALS_REDUCED", key);

        completeIfReady(key);
    }

    @Async
    @EventListener
    public void handleBookLocationsReduced(BookLocationsReducedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getChapterId()
        );
        logBranchArrival(
                "BOOK_LOCATIONS_REDUCED",
                key,
                event.getBookId(),
                "processed=" + event.isProcessed()
                        + ", chapterLocationCount=" + event.getChapterLocationCount()
                        + ", bookLocationCount=" + event.getBookLocationCount()
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.bookLocationsReducedEvent = event;
            return next;
        });

        logCoordinatorState("BOOK_LOCATIONS_REDUCED", key);

        completeIfReady(key);
    }

    private void completeIfReady(CompletionKey key) {
        completionStates.computeIfPresent(key, (ignored, state) -> {
            if (state.completed
                    || state.embeddingsCompletedEvent == null
                    || state.bookIndividualsReducedEvent == null
                    || state.bookLocationsReducedEvent == null) {
                return state;
            }

            UUID jobId = key.jobId();
            UUID chapterId = key.chapterId();
            EmbeddingsCompletedEvent embeddingsEvent = state.embeddingsCompletedEvent;
            UUID bookId = state.bookIndividualsReducedEvent != null
                    ? state.bookIndividualsReducedEvent.getBookId()
                    : state.bookLocationsReducedEvent != null ? state.bookLocationsReducedEvent.getBookId() : null;

            log.info(
                    "[INGESTION_COMPLETION] Ready to complete: jobId={}, chapterId={}, bookId={}, satisfied={}, pending=[]",
                    jobId,
                    chapterId,
                    bookId,
                    satisfiedBranches(state)
            );

            jobRepo.findById(jobId).ifPresent(job -> {
                StatusRecord currentStatus = job.getCurrentStatus();
                if (currentStatus != null
                        && currentStatus.getStatus() == IngestionStatus.FAILED) {
                    log.warn(
                            "[INGESTION_COMPLETION] Skipping completion for failed job: jobId={}, chapterId={}, bookId={}, satisfied={}, pending=[]",
                            jobId,
                            chapterId,
                            bookId,
                            satisfiedBranches(state)
                    );
                    return;
                }

                ingestionJobService.completeJob(job, chapterId, embeddingsEvent.getChapterLength());
                eventPublisher.publishEvent(new IngestionCompletedEvent(
                        this,
                        jobId,
                        chapterId,
                        embeddingsEvent.getTotalScenes(),
                        embeddingsEvent.getTotalChunks(),
                        embeddingsEvent.getTotalEmbeddings()
                ));
                log.info(
                        "[INGESTION_COMPLETION] Completed: jobId={}, chapterId={}, bookId={}, totalScenes={}, totalChunks={}, totalEmbeddings={}",
                        jobId,
                        chapterId,
                        bookId,
                        embeddingsEvent.getTotalScenes(),
                        embeddingsEvent.getTotalChunks(),
                        embeddingsEvent.getTotalEmbeddings()
                );
            });

            state.completed = true;
            return null;
        });
    }

    private record CompletionKey(UUID jobId, UUID chapterId) {
    }

    private void logBranchArrival(String branch, CompletionKey key, UUID bookId, String details) {
        log.info(
                "[INGESTION_COMPLETION] Branch received: jobId={}, chapterId={}, bookId={}, branch={}, {}",
                key.jobId(),
                key.chapterId(),
                bookId,
                branch,
                details
        );
    }

    private void logCoordinatorState(String branch, CompletionKey key) {
        CompletionState state = completionStates.get(key);
        if (state == null) {
            return;
        }

        UUID bookId = state.bookIndividualsReducedEvent != null
                ? state.bookIndividualsReducedEvent.getBookId()
                : state.bookLocationsReducedEvent != null ? state.bookLocationsReducedEvent.getBookId() : null;

        log.info(
                "[INGESTION_COMPLETION] Waiting after branch: jobId={}, chapterId={}, bookId={}, branch={}, satisfied={}, pending={}",
                key.jobId(),
                key.chapterId(),
                bookId,
                branch,
                satisfiedBranches(state),
                pendingBranches(state)
        );
    }

    private String satisfiedBranches(CompletionState state) {
        StringBuilder builder = new StringBuilder("[");
        appendBranch(builder, state.embeddingsCompletedEvent != null, "EMBEDDINGS_COMPLETED");
        appendBranch(builder, state.bookIndividualsReducedEvent != null, "BOOK_INDIVIDUALS_REDUCED");
        appendBranch(builder, state.bookLocationsReducedEvent != null, "BOOK_LOCATIONS_REDUCED");
        builder.append("]");
        return builder.toString();
    }

    private String pendingBranches(CompletionState state) {
        StringBuilder builder = new StringBuilder("[");
        appendBranch(builder, state.embeddingsCompletedEvent == null, "EMBEDDINGS_COMPLETED");
        appendBranch(builder, state.bookIndividualsReducedEvent == null, "BOOK_INDIVIDUALS_REDUCED");
        appendBranch(builder, state.bookLocationsReducedEvent == null, "BOOK_LOCATIONS_REDUCED");
        builder.append("]");
        return builder.toString();
    }

    private void appendBranch(StringBuilder builder, boolean include, String branch) {
        if (!include) {
            return;
        }
        if (builder.length() > 1) {
            builder.append(", ");
        }
        builder.append(branch);
    }

    private static final class CompletionState {
        private EmbeddingsCompletedEvent embeddingsCompletedEvent;
        private BookIndividualsReducedEvent bookIndividualsReducedEvent;
        private BookLocationsReducedEvent bookLocationsReducedEvent;
        private boolean completed;
    }
}
