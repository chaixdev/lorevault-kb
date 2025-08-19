package com.lorevault.api.web.system;

import com.lorevault.api.service.system.LlmHealthCheckService;
import com.lorevault.api.service.system.LlmChatSlotsHealthService;
import com.lorevault.api.service.system.EmbeddingHealthCheckService;
import com.lorevault.api.service.system.metrics.HealthMetricsCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController("systemHealthController")
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final Optional<BuildProperties> buildProperties;
    private final LlmHealthCheckService llmHealthCheckService;
    private final LlmChatSlotsHealthService chatSlotsHealthService;
    private final EmbeddingHealthCheckService embeddingHealthCheckService;

    @GetMapping
    public Map<String, Object> getHealth() {
        boolean llmHealthy = llmHealthCheckService.isLlmServiceHealthy();
        var embStatus = embeddingHealthCheckService.getLastStatus();
        boolean embHealthy = embStatus.healthy();
        boolean overall = llmHealthy && embHealthy;

        Map<String, Object> embeddingsMap = new HashMap<>();
        embeddingsMap.put("healthy", embHealthy);
        embeddingsMap.put("dimension", embStatus.dimension());
        embeddingsMap.put("durationMs", embStatus.durationMs());
        if (!embHealthy && embStatus.error() != null) {
            embeddingsMap.put("error", embStatus.error());
        }

        Map<String, Object> chatSlots = new HashMap<>();
        chatSlotsHealthService.checkSlots().forEach((slot, status) -> {
            chatSlots.put(slot, Map.of(
                "healthy", status.isHealthy(),
                "model", status.getModelName(),
                "lastAttemptMs", status.getLastAttemptDurationMs()
            ));
        });

        Map<String, Object> checks = new HashMap<>();
        checks.put("llm", Map.of(
            "healthy", llmHealthy,
            "description", "Large Language Model API connectivity",
            "slots", chatSlots
        ));
        checks.put("embeddings", embeddingsMap);

        return Map.of(
            "healthy", overall,
            "service", buildProperties.map(BuildProperties::getName).orElse("LoreVault API"),
            "version", buildProperties.map(BuildProperties::getVersion).orElse("unknown"),
            "timestamp", Instant.now().toString(),
            "checks", checks
        );
    }

    @GetMapping("/llm")
    public Map<String, Object> getLlmHealth() {
        var modelResults = llmHealthCheckService.checkAllModels();
        boolean allHealthy = modelResults.values().stream().allMatch(HealthMetricsCollector.ModelHealthStatus::isHealthy);
        Map<String, Object> models = new HashMap<>();
        modelResults.forEach((modelId, status) -> {
            models.put(modelId, Map.of(
                "healthy", status.isHealthy(),
                "name", status.getModelName(),
                "status", status.isHealthy() ? "operational" : "error"
            ));
        });
        Map<String, Object> slots = new HashMap<>();
        chatSlotsHealthService.checkSlots().forEach((slot, status) -> {
            slots.put(slot, Map.of(
                "healthy", status.isHealthy(),
                "model", status.getModelName(),
                "lastAttemptMs", status.getLastAttemptDurationMs(),
                "status", status.isHealthy() ? "operational" : "error"
            ));
        });
        return Map.of(
            "healthy", allHealthy,
            "service", "LLM API",
            "timestamp", Instant.now().toString(),
            "description", allHealthy ? "All models operational" : "One or more models have issues",
            "models", models,
            "slots", slots
        );
    }

    @GetMapping("/embeddings")
    public Map<String, Object> getEmbeddingHealth() {
        var status = embeddingHealthCheckService.checkEmbeddingService();
        Map<String, Object> m = new HashMap<>();
        m.put("healthy", status.healthy());
        m.put("dimension", status.dimension());
        m.put("durationMs", status.durationMs());
        if (!status.healthy() && status.error() != null) m.put("error", status.error());
        return m;
    }
}
