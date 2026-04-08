package com.lorevault.api.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Retry strategy specifically designed for LLM interactions.
 * Provides retry logic with jitter for LLM API calls, broken connections,
 * and response validation (parsing) failures.
 */
@Component
@Slf4j
public class LlmRetryStrategy {
    
    private static final Random random = new Random();
    
    /**
     * Simple configuration for LLM retry behavior
     */
    public static class LlmRetryConfig {
        private final int maxAttempts;
        private final long baseDelayMs;
        private final double backoffMultiplier;
        private final long maxDelayMs;
        private final double jitterFactor;
        
        private LlmRetryConfig(int maxAttempts, long baseDelayMs, double backoffMultiplier, 
                              long maxDelayMs, double jitterFactor) {
            this.maxAttempts = maxAttempts;
            this.baseDelayMs = baseDelayMs;
            this.backoffMultiplier = backoffMultiplier;
            this.maxDelayMs = maxDelayMs;
            this.jitterFactor = jitterFactor;
        }
        
        public static LlmRetryConfig defaultConfig() {
            return new LlmRetryConfig(4, 2000, 2.0, 30000, 0.1);
        }
        
        // Getters
        public int getMaxAttempts() { return maxAttempts; }
        public long getBaseDelayMs() { return baseDelayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public double getJitterFactor() { return jitterFactor; }
    }
    
    /**
     * Result of an LLM retry operation with metadata
     */
    public static class LlmRetryResult<T> {
        private final boolean success;
        private final T result;
        private final Exception lastException;
        private final int attemptsUsed;
        private final long totalDurationMs;
        private final List<String> attemptDetails;
        
        private LlmRetryResult(boolean success, T result, Exception lastException, 
                              int attemptsUsed, long totalDurationMs, List<String> attemptDetails) {
            this.success = success;
            this.result = result;
            this.lastException = lastException;
            this.attemptsUsed = attemptsUsed;
            this.totalDurationMs = totalDurationMs;
            this.attemptDetails = attemptDetails;
        }
        
        public static <T> LlmRetryResult<T> success(T result, int attemptsUsed, 
                                                   long totalDurationMs, List<String> attemptDetails) {
            return new LlmRetryResult<>(true, result, null, attemptsUsed, totalDurationMs, attemptDetails);
        }
        
        public static <T> LlmRetryResult<T> failure(Exception lastException, int attemptsUsed, 
                                                   long totalDurationMs, List<String> attemptDetails) {
            return new LlmRetryResult<>(false, null, lastException, attemptsUsed, totalDurationMs, attemptDetails);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public T getResult() { return result; }
        public Exception getLastException() { return lastException; }
        public int getAttemptsUsed() { return attemptsUsed; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public List<String> getAttemptDetails() { return attemptDetails; }
    }
    
    /**
     * Execute an LLM operation with retry logic and jitter
     */
    public <T> LlmRetryResult<T> executeWithRetry(
            String operationName,
            LlmRetryConfig config,
            Supplier<T> operation) {
        
        long startTime = System.currentTimeMillis();
        List<String> attemptDetails = new ArrayList<>();
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                log.debug("[LLM-Retry] {} attempt {}/{}", operationName, attempt, config.getMaxAttempts());
                
                T result = operation.get();
                
                attemptDetails.add(String.format("Attempt %d: Success", attempt));
                long totalDuration = System.currentTimeMillis() - startTime;
                log.debug("✅ {} succeeded on attempt {}/{} in {} ms", 
                         operationName, attempt, config.getMaxAttempts(), totalDuration);
                
                return LlmRetryResult.success(result, attempt, totalDuration, attemptDetails);
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                attemptDetails.add(String.format("Attempt %d: %s - %s", attempt, e.getClass().getSimpleName(), errorMsg));
                
                log.warn("[LLM-Retry] {} attempt {}/{} failed: {}", 
                        operationName, attempt, config.getMaxAttempts(), errorMsg);
                
                // Don't wait after the last attempt
                if (attempt < config.getMaxAttempts()) {
                    waitWithJitter(config, attempt);
                }
            }
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        
        log.error("❌ {} failed permanently after {} attempts in {} ms: {}", 
                 operationName, config.getMaxAttempts(), totalDuration, 
                 lastException != null ? lastException.getMessage() : "Unknown error");
        
        return LlmRetryResult.failure(lastException, config.getMaxAttempts(), totalDuration, attemptDetails);
    }
    
    /**
     * Calculate delay with exponential backoff and jitter
     */
    private void waitWithJitter(LlmRetryConfig config, int attempt) {
        long baseDelay = (long) (config.getBaseDelayMs() * 
                                Math.pow(config.getBackoffMultiplier(), attempt - 1));
        long cappedDelay = Math.min(baseDelay, config.getMaxDelayMs());
        
        // Add jitter to prevent thundering herd
        double jitterRange = cappedDelay * config.getJitterFactor();
        long jitter = (long) ((random.nextDouble() - 0.5) * 2 * jitterRange);
        long finalDelay = Math.max(100, cappedDelay + jitter); // Minimum 100ms
        
        try {
            log.debug("[LLM-Retry] Waiting {} ms before next attempt (base={}, jitter={})", 
                     finalDelay, cappedDelay, jitter);
            Thread.sleep(finalDelay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ie);
        }
    }
}
