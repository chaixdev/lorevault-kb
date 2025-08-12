package com.lorevault.api.service.system;

import com.lorevault.api.application.port.EmbeddingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Health check service for the embedding model endpoint.
 * Performs an optional lightweight embedding call to verify availability and dimension.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingHealthCheckService {

    private final EmbeddingPort embeddingPort;

    @Value("${lorevault.embedding.health.enabled:true}")
    private boolean healthEnabled;

    @Value("${lorevault.embedding.health.test-text:health_check}")
    private String testText;

    @Value("${lorevault.embedding.health.expected-dim:#{null}}")
    private Integer configuredExpectedDim; // optional override; otherwise use embeddingPort.getDimension()

    private volatile HealthStatus lastStatus = new HealthStatus(true, "SKIPPED", 0, 0);

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!healthEnabled) {
            log.info("Embedding health check disabled via lorevault.embedding.health.enabled=false");
            return;
        }
        this.lastStatus = checkEmbeddingService();
        if (lastStatus.healthy()) {
            log.info("✅ Embedding model '{}' healthy (dim={} ms={})", embeddingPort.getModelId(), lastStatus.dimension(), lastStatus.durationMs());
        } else {
            log.error("❌ Embedding model '{}' health check FAILED: {} (ms={})", embeddingPort.getModelId(), lastStatus.error(), lastStatus.durationMs());
        }
    }

    public HealthStatus checkEmbeddingService() {
        if (!healthEnabled) return new HealthStatus(true, "DISABLED", 0, 0);
        Instant start = Instant.now();
        try {
            double[] vec = embeddingPort.embed(testText);
            long ms = Duration.between(start, Instant.now()).toMillis();
            int dim = vec == null ? 0 : vec.length;
            int expected = configuredExpectedDim != null ? configuredExpectedDim : embeddingPort.getDimension();
            if (dim == 0) {
                return new HealthStatus(false, "Empty vector returned", ms, dim);
            }
            if (expected > 0 && dim != expected) {
                return new HealthStatus(false, "Dimension mismatch expected=" + expected + " actual=" + dim, ms, dim);
            }
            return new HealthStatus(true, null, ms, dim);
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            return new HealthStatus(false, e.getMessage(), ms, 0);
        }
    }

    public HealthStatus getLastStatus() {
        return lastStatus;
    }

    public record HealthStatus(boolean healthy, String error, long durationMs, int dimension) {}
}
