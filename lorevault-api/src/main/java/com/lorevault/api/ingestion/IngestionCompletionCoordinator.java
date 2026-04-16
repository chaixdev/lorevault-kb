package com.lorevault.api.ingestion;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;

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
                readUuidField(event, "jobId"),
                readUuidField(event, "chapterId")
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
        CompletionKey key = new CompletionKey(
                readUuidField(event, "jobId"),
                readUuidField(event, "chapterId")
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
        CompletionKey key = new CompletionKey(
                readUuidField(event, "jobId"),
                readUuidField(event, "chapterId")
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

            jobRepo.findById(jobId).ifPresent(job -> {
                StatusRecord currentStatus = readField(job, "currentStatus", StatusRecord.class);
                if (currentStatus != null
                        && readField(currentStatus, "status", IngestionStatus.class) == IngestionStatus.FAILED) {
                    log.warn("[INGESTION_COMPLETION] Skipping completion event for failed job={} chapter={}", jobId, chapterId);
                    return;
                }

                ingestionJobService.completeJob(job, chapterId, readField(embeddingsEvent, "chapterLength", Integer.class));
                eventPublisher.publishEvent(new IngestionCompletedEvent(
                        this,
                        jobId,
                        chapterId,
                        readField(embeddingsEvent, "totalScenes", Integer.class),
                        readField(embeddingsEvent, "totalChunks", Integer.class),
                        readField(embeddingsEvent, "totalEmbeddings", Integer.class)
                ));
                log.info("[INGESTION_COMPLETION] Completed coordinated ingestion for job={}, chapter={}", jobId, chapterId);
            });

            state.completed = true;
            return null;
        });
    }

    private record CompletionKey(UUID jobId, UUID chapterId) {
    }

    private UUID readUuidField(Object target, String fieldName) {
        return readField(target, fieldName, UUID.class);
    }

    private <T> T readField(Object target, String fieldName, Class<T> type) {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found on " + target.getClass().getName());
        }

        try {
            field.setAccessible(true);
            Object value = field.get(target);
            return type.cast(value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to read field '" + fieldName + "' on " + target.getClass().getName(), e);
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static final class CompletionState {
        private EmbeddingsCompletedEvent embeddingsCompletedEvent;
        private BookIndividualsReducedEvent bookIndividualsReducedEvent;
        private BookLocationsReducedEvent bookLocationsReducedEvent;
        private boolean completed;
    }
}
