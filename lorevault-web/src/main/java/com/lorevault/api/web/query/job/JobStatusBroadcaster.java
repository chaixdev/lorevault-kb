package com.lorevault.api.web.query.job;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
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
    void onStageCompleted(StageCompletedEvent event) {
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

    private Map<String, Object> buildPayload(StageCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "STAGE_COMPLETED");
        payload.put("jobId", event.getJobId());
        payload.put("chapterId", event.getChapterId());
        payload.put("bookId", event.getBookId());
        payload.put("stage", event.getStage().name());
        payload.put("success", event.getResult() != null && event.getResult().success());
        payload.put("summary", event.getResult() != null ? event.getResult().summary() : "");
        payload.put("counts", event.getResult() != null ? event.getResult().counts() : Map.of());
        payload.put("elapsedMs", event.getResult() != null ? event.getResult().durationMs() : 0);
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
