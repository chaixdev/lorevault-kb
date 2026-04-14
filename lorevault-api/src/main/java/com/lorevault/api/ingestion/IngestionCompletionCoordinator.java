package com.lorevault.api.ingestion;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
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
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        CompletionKey key = new CompletionKey(
                (UUID) eventBean.getPropertyValue("jobId"),
                (UUID) eventBean.getPropertyValue("chapterId")
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.embeddingsCompletedEvent = event;
            return next;
        });

        completeIfReady(key);
    }

    @Async
    @EventListener
    public void handleBookIndividualsReduced(BookIndividualsReducedEvent event) {
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        CompletionKey key = new CompletionKey(
                (UUID) eventBean.getPropertyValue("jobId"),
                (UUID) eventBean.getPropertyValue("chapterId")
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.bookIndividualsReducedEvent = event;
            return next;
        });

        completeIfReady(key);
    }

    @Async
    @EventListener
    public void handleBookLocationsReduced(BookLocationsReducedEvent event) {
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        CompletionKey key = new CompletionKey(
                (UUID) eventBean.getPropertyValue("jobId"),
                (UUID) eventBean.getPropertyValue("chapterId")
        );

        completionStates.compute(key, (ignored, current) -> {
            CompletionState next = current == null ? new CompletionState() : current;
            next.bookLocationsReducedEvent = event;
            return next;
        });

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
            BeanWrapperImpl embeddingsBean = new BeanWrapperImpl(embeddingsEvent);

            jobRepo.findById(jobId).ifPresent(job -> {
                ingestionJobService.completeJob(job, chapterId, (Integer) embeddingsBean.getPropertyValue("chapterLength"));
                eventPublisher.publishEvent(new IngestionCompletedEvent(
                        this,
                        jobId,
                        chapterId,
                        (Integer) embeddingsBean.getPropertyValue("totalScenes"),
                        (Integer) embeddingsBean.getPropertyValue("totalChunks"),
                        (Integer) embeddingsBean.getPropertyValue("totalEmbeddings")
                ));
                log.info("[INGESTION_COMPLETION] Completed coordinated ingestion for job={}, chapter={}", jobId, chapterId);
            });

            state.completed = true;
            return null;
        });
    }

    private record CompletionKey(UUID jobId, UUID chapterId) {
    }

    private static final class CompletionState {
        private EmbeddingsCompletedEvent embeddingsCompletedEvent;
        private BookIndividualsReducedEvent bookIndividualsReducedEvent;
        private BookLocationsReducedEvent bookLocationsReducedEvent;
        private boolean completed;
    }
}
