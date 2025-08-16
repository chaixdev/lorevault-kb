package com.lorevault.api.service.system.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and manages health check metrics and timing data.
 * Provides structured data for monitoring and diagnostics.
 * Extracted from LlmHealthCheckService to improve single responsibility and testability.
 */
@Component
@Slf4j
public class HealthMetricsCollector {

    private final ConcurrentHashMap<String, ModelMetrics> modelMetrics = new ConcurrentHashMap<>();

    /**
     * Comprehensive health status with timing and attempt metadata
     */
    public static class ModelHealthStatus {
        private final boolean healthy;
        private final String modelName;
        private final String message;
        private final long lastAttemptDurationMs;
        private final long totalDurationMs;
        private final int attemptsUsed;
        private final Instant timestamp;

        public ModelHealthStatus(boolean healthy, String modelName, String message, 
                               long lastAttemptDurationMs, long totalDurationMs, int attemptsUsed) {
            this.healthy = healthy;
            this.modelName = modelName;
            this.message = message;
            this.lastAttemptDurationMs = lastAttemptDurationMs;
            this.totalDurationMs = totalDurationMs;
            this.attemptsUsed = attemptsUsed;
            this.timestamp = Instant.now();
        }

        public boolean isHealthy() { return healthy; }
        public String getModelName() { return modelName; }
        public String getErrorMessage() { return healthy ? null : message; }
        public String getSuccessMessage() { return healthy ? message : null; }
        public long getLastAttemptDurationMs() { return lastAttemptDurationMs; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public int getAttemptsUsed() { return attemptsUsed; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Metrics tracking for a specific model
     */
    public static class ModelMetrics {
        private final AtomicLong totalChecks = new AtomicLong(0);
        private final AtomicLong successfulChecks = new AtomicLong(0);
        private final AtomicLong failedChecks = new AtomicLong(0);
        private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
        private final AtomicLong totalAttempts = new AtomicLong(0);
        private volatile ModelHealthStatus lastStatus;
        private volatile Instant firstCheckTime;
        private volatile Instant lastCheckTime;

        public long getTotalChecks() { return totalChecks.get(); }
        public long getSuccessfulChecks() { return successfulChecks.get(); }
        public long getFailedChecks() { return failedChecks.get(); }
        public long getTotalResponseTimeMs() { return totalResponseTimeMs.get(); }
        public long getTotalAttempts() { return totalAttempts.get(); }
        public ModelHealthStatus getLastStatus() { return lastStatus; }
        public Instant getFirstCheckTime() { return firstCheckTime; }
        public Instant getLastCheckTime() { return lastCheckTime; }

        public double getSuccessRate() {
            long total = getTotalChecks();
            return total > 0 ? (double) getSuccessfulChecks() / total : 0.0;
        }

        public double getAverageResponseTimeMs() {
            long total = getTotalChecks();
            return total > 0 ? (double) getTotalResponseTimeMs() / total : 0.0;
        }

        public double getAverageAttemptsPerCheck() {
            long total = getTotalChecks();
            return total > 0 ? (double) getTotalAttempts() / total : 0.0;
        }
    }

    /**
     * Record a successful health check result
     */
    public ModelHealthStatus recordSuccess(String modelName, String successMessage, 
                                         long lastAttemptDurationMs, long totalDurationMs, int attemptsUsed) {
        
        ModelHealthStatus status = new ModelHealthStatus(
            true, modelName, successMessage, lastAttemptDurationMs, totalDurationMs, attemptsUsed
        );

        updateMetrics(modelName, status, true);
        logHealthCheckResult(status, true);
        
        return status;
    }

    /**
     * Record a failed health check result
     */
    public ModelHealthStatus recordFailure(String modelName, String errorMessage, 
                                         long lastAttemptDurationMs, long totalDurationMs, int attemptsUsed) {
        
        ModelHealthStatus status = new ModelHealthStatus(
            false, modelName, errorMessage, lastAttemptDurationMs, totalDurationMs, attemptsUsed
        );

        updateMetrics(modelName, status, false);
        logHealthCheckResult(status, false);
        
        return status;
    }

    /**
     * Get current metrics for a specific model
     */
    public ModelMetrics getModelMetrics(String modelName) {
        return modelMetrics.get(modelName);
    }

    /**
     * Get the last recorded status for a model
     */
    public ModelHealthStatus getLastStatus(String modelName) {
        ModelMetrics metrics = modelMetrics.get(modelName);
        return metrics != null ? metrics.getLastStatus() : null;
    }

    /**
     * Check if a model has been checked recently and was healthy
     */
    public boolean isModelCurrentlyHealthy(String modelName) {
        ModelHealthStatus lastStatus = getLastStatus(modelName);
        return lastStatus != null && lastStatus.isHealthy();
    }

    /**
     * Reset metrics for a specific model (useful for testing)
     */
    public void resetModelMetrics(String modelName) {
        modelMetrics.remove(modelName);
        log.debug("[Health-Metrics] Reset metrics for model: {}", modelName);
    }

    /**
     * Reset all metrics (useful for testing)
     */
    public void resetAllMetrics() {
        modelMetrics.clear();
        log.debug("[Health-Metrics] Reset all model metrics");
    }

    /**
     * Update internal metrics tracking
     */
    private void updateMetrics(String modelName, ModelHealthStatus status, boolean success) {
        ModelMetrics metrics = modelMetrics.computeIfAbsent(modelName, k -> new ModelMetrics());
        
        // Update timestamps
        Instant now = Instant.now();
        if (metrics.firstCheckTime == null) {
            metrics.firstCheckTime = now;
        }
        metrics.lastCheckTime = now;
        
        // Update counters
        metrics.totalChecks.incrementAndGet();
        if (success) {
            metrics.successfulChecks.incrementAndGet();
        } else {
            metrics.failedChecks.incrementAndGet();
        }
        
        // Update timing metrics
        metrics.totalResponseTimeMs.addAndGet(status.getTotalDurationMs());
        metrics.totalAttempts.addAndGet(status.getAttemptsUsed());
        
        // Store last status
        metrics.lastStatus = status;
    }

    /**
     * Log health check results with appropriate level
     */
    private void logHealthCheckResult(ModelHealthStatus status, boolean success) {
        if (success) {
            log.debug("✅ Model '{}' health check succeeded in {} ms (attempts: {})", 
                     status.getModelName(), status.getTotalDurationMs(), status.getAttemptsUsed());
        } else {
            log.warn("❌ Model '{}' health check failed after {} ms (attempts: {}): {}", 
                    status.getModelName(), status.getTotalDurationMs(), 
                    status.getAttemptsUsed(), status.getErrorMessage());
        }
    }
}
