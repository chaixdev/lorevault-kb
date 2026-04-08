package com.lorevault.api.health;

import com.lorevault.api.health.SystemHealthService;
import com.lorevault.api.health.HealthMetricsCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges SystemHealthService into Spring Boot Actuator's /actuator/health endpoint.
 * Provides rich details under standard health endpoint rather than custom endpoints.
 */
@Component
@Primary
@RequiredArgsConstructor
public class SystemHealthIndicator implements HealthIndicator {

    private final SystemHealthService systemHealthService;

    @Override
    public Health health() {
        var system = systemHealthService.getOverallSystemHealth();

        Map<String, Object> details = new HashMap<>();

        // Embedding details
        var emb = system.embeddingHealth();
        Map<String, Object> embeddings = new HashMap<>();
        embeddings.put("healthy", emb.healthy());
        embeddings.put("dimension", emb.dimension());
        embeddings.put("durationMs", emb.durationMs());
        if (!emb.healthy() && emb.error() != null) embeddings.put("error", emb.error());
        details.put("embeddings", embeddings);

        // LLM and slots details
        Map<String, Object> llm = new HashMap<>();
        llm.put("healthy", system.llmHealth().isHealthy());
        llm.put("model", system.llmHealth().getModelName());
        Map<String, Object> slots = new HashMap<>();
        for (Map.Entry<String, HealthMetricsCollector.ModelHealthStatus> e : system.chatSlotsHealth().entrySet()) {
            var s = e.getValue();
            Map<String, Object> slot = new HashMap<>();
            slot.put("healthy", s.isHealthy());
            slot.put("model", s.getModelName());
            slot.put("lastAttemptMs", s.getLastAttemptDurationMs());
            slots.put(e.getKey(), slot);
        }
        llm.put("slots", slots);
        details.put("llm", llm);

        if (system.isOverallHealthy()) {
            return Health.up().withDetails(details).build();
        } else {
            return Health.down().withDetails(details).build();
        }
    }
}
