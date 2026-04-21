package com.lorevault.api.web.query.job;

import com.lorevault.api.ingestion.events.ChunksCreatedEvent;
import com.lorevault.api.ingestion.events.IngestionCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class JobStatusBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        sendComment(emitter, "connected");
        log.debug("SSE client registered ({} total)", emitters.size());
        return emitter;
    }

    public int getConnectionCount() {
        return emitters.size();
    }

    @EventListener
    void onStatusUpdate(IngestionEvent event) {
        broadcast("status-update", buildPayload(event));
    }

    @Scheduled(fixedRate = 30_000)
    void keepAlive() {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private Map<String, Object> buildPayload(IngestionEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.getEventType());
        payload.put("jobId", event.getJobId());
        payload.put("chapterId", event.getChapterId());
        payload.put("timestamp", event.getEventTime().toString());

        if (event instanceof ScenesDetectedEvent sceneEvent) {
            payload.put("sceneCount", sceneEvent.getSceneCount());
            payload.put("bookId", sceneEvent.getBookId());
        } else if (event instanceof ChunksCreatedEvent chunkEvent) {
            payload.put("chunkCount", chunkEvent.getChunkCount());
            payload.put("bookId", chunkEvent.getBookId());
        } else if (event instanceof IngestionCompletedEvent completeEvent) {
            payload.put("totalScenes", completeEvent.getTotalScenes());
            payload.put("totalChunks", completeEvent.getTotalChunks());
            payload.put("totalEmbeddings", completeEvent.getTotalEmbeddings());
        } else if (event instanceof IngestionFailedEvent failedEvent) {
            payload.put("failedStage", failedEvent.getFailedStage());
            payload.put("errorMessage", failedEvent.getErrorMessage());
            payload.put("retryable", failedEvent.isRetryable());
        }

        return payload;
    }

    private void sendComment(SseEmitter emitter, String comment) {
        try {
            emitter.send(SseEmitter.event().comment(comment));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
    }
}
