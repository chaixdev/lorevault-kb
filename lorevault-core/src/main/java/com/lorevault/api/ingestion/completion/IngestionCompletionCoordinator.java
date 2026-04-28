package com.lorevault.api.ingestion.completion;

import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.job.StatusRecord;
import com.lorevault.api.ingestion.job.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.events.BookEventCandidatesGeneratedEvent;
import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.EmbeddingsCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class IngestionCompletionCoordinator {

    private static final int MAX_RETAINED_TERMINAL_FAILURES = 10_000;

    private final IngestionJobGraphRepository jobRepo;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<CompletionKey, CompletionState> completionStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CompletionKey, Long> terminalFailures = new ConcurrentHashMap<>();

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleEmbeddingsCompleted(EmbeddingsCompletedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getCorrelationId(),
                event.getChapterId()
        );
        if (isTerminalFailure(key, "EMBEDDINGS_COMPLETED")) {
            return;
        }
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

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleBookIndividualsReduced(BookIndividualsReducedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getCorrelationId(),
                event.getChapterId()
        );
        if (isTerminalFailure(key, "BOOK_INDIVIDUALS_REDUCED")) {
            return;
        }
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

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleBookLocationsReduced(BookLocationsReducedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getCorrelationId(),
                event.getChapterId()
        );
        if (isTerminalFailure(key, "BOOK_LOCATIONS_REDUCED")) {
            return;
        }
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

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleChapterEventsResolved(ChapterEventsResolvedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getCorrelationId(),
                event.getChapterId()
        );
        if (isTerminalFailure(key, "CHAPTER_EVENTS_RESOLVED")) {
            return;
        }
        logBranchArrival(
                "CHAPTER_EVENTS_RESOLVED",
                key,
                event.getBookId(),
                "processed=" + event.isProcessed()
                        + ", mentionCount=" + event.getMentionCount()
                        + ", chapterEventCount=" + event.getChapterEventCount()
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.chapterEventsResolvedEvent = event;
            return next;
        });

        logCoordinatorState("CHAPTER_EVENTS_RESOLVED", key);

        completeIfReady(key);
    }

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleBookEventCandidatesGenerated(BookEventCandidatesGeneratedEvent event) {
        CompletionKey key = new CompletionKey(
                event.getJobId(),
                event.getCorrelationId(),
                event.getChapterId()
        );
        if (isTerminalFailure(key, "BOOK_EVENT_CANDIDATES_GENERATED")) {
            return;
        }
        logBranchArrival(
                "BOOK_EVENT_CANDIDATES_GENERATED",
                key,
                event.getBookId(),
                "embeddedCount=" + event.getEmbeddedCount()
                        + ", candidatePairCount=" + event.getCandidatePairCount()
                        + ", bookEventsCreated=" + event.getBookEventsCreated()
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.bookEventCandidatesGeneratedEvent = event;
            return next;
        });

        logCoordinatorState("BOOK_EVENT_CANDIDATES_GENERATED", key);

        completeIfReady(key);
    }

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleIngestionFailed(IngestionFailedEvent event) {
        CompletionKey key = new CompletionKey(event.getJobId(), event.getCorrelationId(), event.getChapterId());
        terminalFailures.put(key, System.nanoTime());
        pruneTerminalFailures();
        CompletionState removed = completionStates.remove(key);

        log.info(
                "[INGESTION_COMPLETION] Failure cleanup: jobId={}, chapterId={}, failedStage={}, stateRemoved={}",
                event.getJobId(),
                event.getChapterId(),
                event.getFailedStage(),
                removed != null
        );
    }

    private boolean isTerminalFailure(CompletionKey key, String branch) {
        if (!terminalFailures.containsKey(key)) {
            return false;
        }

        log.info(
                "[INGESTION_COMPLETION] Ignoring late branch after failure: jobId={}, chapterId={}, branch={}",
                key.jobId(),
                key.chapterId(),
                branch
        );
        return true;
    }

    private void completeIfReady(CompletionKey key) {
        completionStates.computeIfPresent(key, (ignored, state) -> {
            if (state.embeddingsCompletedEvent == null
                    || state.bookIndividualsReducedEvent == null
                    || state.bookLocationsReducedEvent == null
                    || state.chapterEventsResolvedEvent == null
                    || state.bookEventCandidatesGeneratedEvent == null) {
                return state;
            }

            UUID jobId = key.jobId();
            UUID chapterId = key.chapterId();
            EmbeddingsCompletedEvent embeddingsEvent = state.embeddingsCompletedEvent;
            UUID bookId = resolveBookId(state);

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
                if (currentStatus != null
                        && currentStatus.getStatus() == IngestionStatus.COMPLETE) {
                    log.warn(
                            "[INGESTION_COMPLETION] Skipping duplicate completion for already-completed job: jobId={}, chapterId={}, bookId={}",
                            jobId,
                            chapterId,
                            bookId
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

            return null;
        });
    }

    private void pruneTerminalFailures() {
        while (terminalFailures.size() > MAX_RETAINED_TERMINAL_FAILURES) {
            terminalFailures.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(oldest -> terminalFailures.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    private record CompletionKey(UUID jobId, UUID correlationId, UUID chapterId) {
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

        UUID bookId = resolveBookId(state);

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
        appendBranch(builder, state.chapterEventsResolvedEvent != null, "CHAPTER_EVENTS_RESOLVED");
        appendBranch(builder, state.bookEventCandidatesGeneratedEvent != null, "BOOK_EVENT_CANDIDATES_GENERATED");
        builder.append("]");
        return builder.toString();
    }

    private String pendingBranches(CompletionState state) {
        StringBuilder builder = new StringBuilder("[");
        appendBranch(builder, state.embeddingsCompletedEvent == null, "EMBEDDINGS_COMPLETED");
        appendBranch(builder, state.bookIndividualsReducedEvent == null, "BOOK_INDIVIDUALS_REDUCED");
        appendBranch(builder, state.bookLocationsReducedEvent == null, "BOOK_LOCATIONS_REDUCED");
        appendBranch(builder, state.chapterEventsResolvedEvent == null, "CHAPTER_EVENTS_RESOLVED");
        appendBranch(builder, state.bookEventCandidatesGeneratedEvent == null, "BOOK_EVENT_CANDIDATES_GENERATED");
        builder.append("]");
        return builder.toString();
    }

    private UUID resolveBookId(CompletionState state) {
        if (state.bookIndividualsReducedEvent != null) {
            return state.bookIndividualsReducedEvent.getBookId();
        }
        if (state.bookLocationsReducedEvent != null) {
            return state.bookLocationsReducedEvent.getBookId();
        }
        if (state.chapterEventsResolvedEvent != null) {
            return state.chapterEventsResolvedEvent.getBookId();
        }
        if (state.bookEventCandidatesGeneratedEvent != null) {
            return state.bookEventCandidatesGeneratedEvent.getBookId();
        }
        return null;
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
        private ChapterEventsResolvedEvent chapterEventsResolvedEvent;
        private BookEventCandidatesGeneratedEvent bookEventCandidatesGeneratedEvent;
    }
}
