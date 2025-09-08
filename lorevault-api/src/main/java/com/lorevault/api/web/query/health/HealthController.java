package com.lorevault.api.web.query.health;

import com.lorevault.api.service.system.SystemHealthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController("queryHealthController")
@RequestMapping("/api/query/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "System health monitoring")
public class HealthController {

    private final Optional<BuildProperties> buildProperties;
    private final SystemHealthService systemHealthService;

    @GetMapping
    public Map<String, Object> getHealth() {
        var systemHealth = systemHealthService.getOverallSystemHealth();
        
        boolean llmHealthy = systemHealth.llmHealth().isHealthy();
        var embStatus = systemHealth.embeddingHealth();
        boolean embHealthy = embStatus.healthy();
        boolean overall = systemHealth.isOverallHealthy();

        Map<String, Object> embeddingsMap = new HashMap<>();
        embeddingsMap.put("healthy", embHealthy);
        embeddingsMap.put("dimension", embStatus.dimension());
        embeddingsMap.put("durationMs", embStatus.durationMs());
        if (!embHealthy && embStatus.error() != null) {
            embeddingsMap.put("error", embStatus.error());
        }

        Map<String, Object> chatSlots = new HashMap<>();
        systemHealth.chatSlotsHealth().forEach((slot, status) -> {
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
}
