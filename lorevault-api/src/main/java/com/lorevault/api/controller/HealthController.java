package com.lorevault.api.controller;

import com.lorevault.api.service.LlmHealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final Optional<BuildProperties> buildProperties;
    private final LlmHealthCheckService llmHealthCheckService;

    @GetMapping
    public Map<String, Object> getHealth() {
        boolean llmHealthy = llmHealthCheckService.isLlmServiceHealthy();
        
        return Map.of(
            "healthy", llmHealthy,
            "service", buildProperties.map(BuildProperties::getName).orElse("LoreVault API"),
            "version", buildProperties.map(BuildProperties::getVersion).orElse("unknown"),
            "timestamp", Instant.now().toString(),
            "checks", Map.of(
                "llm", Map.of(
                    "healthy", llmHealthy,
                    "description", "Large Language Model API connectivity"
                )
            )
        );
    }
    
    @GetMapping("/llm")
    public Map<String, Object> getLlmHealth() {
        var modelResults = llmHealthCheckService.checkAllModels();
        boolean allHealthy = modelResults.values().stream().allMatch(LlmHealthCheckService.ModelHealthStatus::isHealthy);
        
        Map<String, Object> models = new HashMap<>();
        modelResults.forEach((modelId, status) -> {
            models.put(modelId, Map.of(
                "healthy", status.isHealthy(),
                "name", status.getModelName(),
                "status", status.isHealthy() ? "operational" : "error"
            ));
        });
        
        return Map.of(
            "healthy", allHealthy,
            "service", "LLM API",
            "timestamp", Instant.now().toString(),
            "description", allHealthy ? "All models operational" : "One or more models have issues",
            "models", models
        );
    }
}
