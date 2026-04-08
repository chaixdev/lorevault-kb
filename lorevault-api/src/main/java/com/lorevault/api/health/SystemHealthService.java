package com.lorevault.api.health;

import com.lorevault.api.config.LoreVaultModelsProperties;
import com.lorevault.api.health.HealthMetricsCollector;
import com.lorevault.api.health.RetryableHealthChecker;
import com.lorevault.api.health.ModelHealthValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified system health service that consolidates health checking across all AI services.
 * Replaces LlmHealthCheckService, EmbeddingHealthCheckService, and LlmChatSlotsHealthService
 * with a single, focused business capability service.
 * 
 * Follows service design principles by grouping related health operations while
 * maintaining clear separation from configuration services like ModelRegistryService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHealthService {

    private final RetryableHealthChecker retryableHealthChecker;
    private final ModelHealthValidator modelHealthValidator;
    private final HealthMetricsCollector healthMetricsCollector;
    @Qualifier("embeddingModel")
    private final EmbeddingModel embeddingModel;
    
    @Qualifier("nlpSmall")
    private final ChatClient nlpSmallChatClient;
    @Qualifier("nlpBig")
    private final ChatClient nlpBigChatClient;
    
    private final LoreVaultModelsProperties modelsProperties;
    private final ModelRegistryService modelRegistryService;
    private final Environment environment;

    @Value("${lorevault.llm.health.enabled:true}")
    private boolean healthEnabled;

    @Value("${lorevault.embedding.health.enabled:true}")
    private boolean embeddingHealthEnabled;

    // Global toggle to skip startup health checks entirely (useful for tests)
    @Value("${lorevault.system.health.startup.enabled:true}")
    private boolean startupHealthCheckEnabled;

    @Value("${lorevault.embedding.health.test-text:health_check}")
    private String embeddingTestText;

    @Value("${lorevault.embedding.health.expected-dim:#{null}}")
    private Integer embeddingExpectedDim;

    private volatile EmbeddingHealthStatus lastEmbeddingStatus = 
        new EmbeddingHealthStatus(true, "SKIPPED", 0, 0);

    /**
     * Performs comprehensive system health check on startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void performStartupHealthCheck() {
        // Allow tests or other environments to disable startup health checks entirely
        if (!startupHealthCheckEnabled) {
            log.info("Skipping system health checks due to lorevault.system.health.startup.enabled=false");
            return;
        }
        // Never run startup health checks during unit/integration tests
        if (environment != null && environment.acceptsProfiles(Profiles.of("test"))) {
            log.info("Skipping system health checks in 'test' profile");
            return;
        }
        if (!healthEnabled) {
            log.info("System health checks disabled via lorevault.llm.health.enabled=false");
            return;
        }
        
        Instant overallStart = Instant.now();
        log.info("Performing comprehensive system health check");
        
        // Check all subsystems
        var llmHealth = checkLlmHealth();
        var embeddingHealth = checkEmbeddingHealth();
        var chatSlotsHealth = checkChatSlotsHealth();
        
        long totalMs = Duration.between(overallStart, Instant.now()).toMillis();
        
        boolean allHealthy = llmHealth.isHealthy() && 
                            embeddingHealth.healthy() &&
                            chatSlotsHealth.values().stream().allMatch(HealthMetricsCollector.ModelHealthStatus::isHealthy);
        
        if (allHealthy) {
            log.info("✅ All system components healthy ({} ms total)", totalMs);
        } else {
            log.error("❌ System health check found issues ({} ms total)", totalMs);
            if (!llmHealth.isHealthy()) log.error("  - LLM health: {}", llmHealth.getErrorMessage());
            if (!embeddingHealth.healthy()) log.error("  - Embedding health: {}", embeddingHealth.error());
            chatSlotsHealth.forEach((slot, status) -> {
                if (!status.isHealthy()) {
                    log.error("  - Chat slot '{}' health: {}", slot, status.getErrorMessage());
                }
            });
        }
    }

    /**
     * Checks the health of the primary LLM service.
     */
    public HealthMetricsCollector.ModelHealthStatus checkLlmHealth() {
        if (!healthEnabled) {
            return new HealthMetricsCollector.ModelHealthStatus(
                true, "disabled", "Health checks disabled", 0, 0, 0);
        }
        
        String modelId = modelRegistryService.getCurrentModelId();
        return checkSingleLlmModel(modelId);
    }

    /**
     * Checks the health of the embedding service.
     */
    public EmbeddingHealthStatus checkEmbeddingHealth() {
        if (!embeddingHealthEnabled) {
            return new EmbeddingHealthStatus(true, "DISABLED", 0, 0);
        }
        
        Instant start = Instant.now();
        try {
            float[] vec = embeddingModel.embed(embeddingTestText);
            long ms = Duration.between(start, Instant.now()).toMillis();
            int dim = vec == null ? 0 : vec.length;
            int expected = embeddingExpectedDim != null ? embeddingExpectedDim : embeddingModel.dimensions();
            
            if (dim == 0) {
                lastEmbeddingStatus = new EmbeddingHealthStatus(false, "Empty vector returned", ms, dim);
                return lastEmbeddingStatus;
            }
            if (expected > 0 && dim != expected) {
                lastEmbeddingStatus = new EmbeddingHealthStatus(
                    false, "Dimension mismatch expected=" + expected + " actual=" + dim, ms, dim);
                return lastEmbeddingStatus;
            }
            
            lastEmbeddingStatus = new EmbeddingHealthStatus(true, null, ms, dim);
            return lastEmbeddingStatus;
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            lastEmbeddingStatus = new EmbeddingHealthStatus(false, e.getMessage(), ms, 0);
            return lastEmbeddingStatus;
        }
    }

    /**
     * Checks the health of both chat client slots (nlp-small and nlp-big).
     */
    public Map<String, HealthMetricsCollector.ModelHealthStatus> checkChatSlotsHealth() {
        Map<String, HealthMetricsCollector.ModelHealthStatus> results = new LinkedHashMap<>();
        results.put("nlp-small", checkChatClientHealth(nlpSmallChatClient, modelsProperties.nlpSmall().model()));
        results.put("nlp-big", checkChatClientHealth(nlpBigChatClient, modelsProperties.nlpBig().model()));
        return results;
    }

    /**
     * Gets comprehensive system health status including all subsystems.
     */
    public SystemHealthResponse getOverallSystemHealth() {
        var llmHealth = checkLlmHealth();
        var embeddingHealth = getLastEmbeddingStatus(); // Use cached to avoid redundant calls
        var chatSlotsHealth = checkChatSlotsHealth();
        
        boolean overallHealthy = llmHealth.isHealthy() && 
                                embeddingHealth.healthy() &&
                                chatSlotsHealth.values().stream().allMatch(HealthMetricsCollector.ModelHealthStatus::isHealthy);
        
        return new SystemHealthResponse(overallHealthy, llmHealth, embeddingHealth, chatSlotsHealth);
    }

    /**
     * Gets the last cached embedding health status.
     */
    public EmbeddingHealthStatus getLastEmbeddingStatus() {
        return lastEmbeddingStatus;
    }

    /**
     * Legacy compatibility method for existing controllers.
     */
    public boolean isLlmServiceHealthy() {
        return checkLlmHealth().isHealthy();
    }

    // Private helper methods

    private HealthMetricsCollector.ModelHealthStatus checkSingleLlmModel(String modelId) {
        String operationName = "health-check-" + modelId;
        RetryableHealthChecker.RetryConfig retryConfig = RetryableHealthChecker.RetryConfig.defaultConfig();
        
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> retryResult = 
            retryableHealthChecker.executeWithRetry(operationName, retryConfig, () -> {
                try {
                    return modelHealthValidator.performConnectivityTest(modelId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        
        if (retryResult.isSuccess()) {
            ModelHealthValidator.HealthCheckResult validationResult = retryResult.getResult();
            return healthMetricsCollector.recordSuccess(
                modelId, 
                validationResult.getResponse(),
                retryResult.getLastAttemptDurationMs(),
                retryResult.getTotalDurationMs(),
                retryResult.getAttemptsUsed()
            );
        } else {
            String errorMessage = retryResult.getLastException() != null 
                ? retryResult.getLastException().getMessage() 
                : "Unknown error";
            return healthMetricsCollector.recordFailure(
                modelId,
                errorMessage,
                retryResult.getLastAttemptDurationMs(),
                retryResult.getTotalDurationMs(),
                retryResult.getAttemptsUsed()
            );
        }
    }

    private HealthMetricsCollector.ModelHealthStatus checkChatClientHealth(ChatClient client, String modelId) {
        String operationName = "health-check-" + modelId;
        RetryableHealthChecker.RetryConfig config = RetryableHealthChecker.RetryConfig.defaultConfig();
        
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> retryResult =
            retryableHealthChecker.executeWithRetry(operationName, config, () -> {
                try {
                    return modelHealthValidator.performConnectivityTestWith(client, modelId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
        if (retryResult.isSuccess()) {
            return new HealthMetricsCollector.ModelHealthStatus(
                true, modelId, "OK", 
                retryResult.getLastAttemptDurationMs(), 
                retryResult.getTotalDurationMs(), 
                retryResult.getAttemptsUsed()
            );
        }
        
        String message = retryResult.getLastException() != null 
            ? retryResult.getLastException().getMessage() 
            : "Unknown error";
        return new HealthMetricsCollector.ModelHealthStatus(
            false, modelId, message,
            retryResult.getLastAttemptDurationMs(),
            retryResult.getTotalDurationMs(),
            retryResult.getAttemptsUsed()
        );
    }

    // Response classes

    public record EmbeddingHealthStatus(boolean healthy, String error, long durationMs, int dimension) {}

    public record SystemHealthResponse(
        boolean isOverallHealthy,
        HealthMetricsCollector.ModelHealthStatus llmHealth,
        EmbeddingHealthStatus embeddingHealth,
        Map<String, HealthMetricsCollector.ModelHealthStatus> chatSlotsHealth
    ) {}
}
