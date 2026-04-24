package com.lorevault.api.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Generic retry mechanism for health check operations.
 * Provides exponential backoff with configurable retry parameters.
 * Extracted from LlmHealthCheckService to improve single responsibility and testability.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryableHealthChecker {

    /**
         * Configuration for retry behavior
         */
        public record RetryConfig(int maxAttempts, long baseDelayMs, double backoffMultiplier, long maxDelayMs) {

        public static RetryConfig defaultConfig() {
                return new RetryConfig(3, 1000, 2.0, 10000);
            }
        }

    /**
     * Result of a retry operation with timing and attempt metadata
     */
    public static class RetryResult<T> {
        private final boolean success;
        private final T result;
        private final Exception lastException;
        private final int attemptsUsed;
        private final long lastAttemptDurationMs;
        private final long totalDurationMs;

        private RetryResult(boolean success, T result, Exception lastException, 
                           int attemptsUsed, long lastAttemptDurationMs, long totalDurationMs) {
            this.success = success;
            this.result = result;
            this.lastException = lastException;
            this.attemptsUsed = attemptsUsed;
            this.lastAttemptDurationMs = lastAttemptDurationMs;
            this.totalDurationMs = totalDurationMs;
        }

        public static <T> RetryResult<T> success(T result, int attemptsUsed, 
                                               long lastAttemptDurationMs, long totalDurationMs) {
            return new RetryResult<>(true, result, null, attemptsUsed, lastAttemptDurationMs, totalDurationMs);
        }

        public static <T> RetryResult<T> failure(Exception lastException, int attemptsUsed, 
                                               long lastAttemptDurationMs, long totalDurationMs) {
            return new RetryResult<>(false, null, lastException, attemptsUsed, lastAttemptDurationMs, totalDurationMs);
        }

        public boolean isSuccess() { return success; }
        public T getResult() { return result; }
        public Exception getLastException() { return lastException; }
        public int getAttemptsUsed() { return attemptsUsed; }
        public long getLastAttemptDurationMs() { return lastAttemptDurationMs; }
        public long getTotalDurationMs() { return totalDurationMs; }
    }

    /**
     * Execute an operation with retry logic and exponential backoff
     */
    public <T> RetryResult<T> executeWithRetry(String operationName, RetryConfig config, 
                                             Supplier<T> operation) {
        Instant overallStart = Instant.now();
        Exception lastException = null;
        long lastAttemptDuration = 0;

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            Instant attemptStart = Instant.now();
            
            try {
                log.debug("[Retry] Executing {} attempt={}/{}", operationName, attempt, config.maxAttempts());
                
                T result = operation.get();
                lastAttemptDuration = Duration.between(attemptStart, Instant.now()).toMillis();
                long totalDuration = Duration.between(overallStart, Instant.now()).toMillis();
                
                log.debug("✅ {} succeeded on attempt {}/{} in {} ms", 
                         operationName, attempt, config.maxAttempts(), lastAttemptDuration);
                
                return RetryResult.success(result, attempt, lastAttemptDuration, totalDuration);
                
            } catch (Exception e) {
                lastAttemptDuration = Duration.between(attemptStart, Instant.now()).toMillis();
                lastException = e;
                
                log.warn("[Retry] {} attempt {}/{} failed in {} ms: {}", 
                        operationName, attempt, config.maxAttempts(), lastAttemptDuration, e.getMessage());
                
                // If this is the last attempt, don't wait
                if (attempt == config.maxAttempts()) {
                    long totalDuration = Duration.between(overallStart, Instant.now()).toMillis();
                    return RetryResult.failure(lastException, attempt, lastAttemptDuration, totalDuration);
                }
                
                // Calculate delay with exponential backoff
                long delayMs = calculateDelay(config, attempt);
                
                try {
                    log.debug("[Retry] Waiting {} ms before next attempt", delayMs);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    long totalDuration = Duration.between(overallStart, Instant.now()).toMillis();
                    return RetryResult.failure(
                        new RuntimeException("Retry interrupted", ie), 
                        attempt, lastAttemptDuration, totalDuration
                    );
                }
            }
        }
        
        // This should never be reached, but just in case
        long totalDuration = Duration.between(overallStart, Instant.now()).toMillis();
        return RetryResult.failure(
            new RuntimeException("Unexpected end of retry loop"), 
            config.maxAttempts(), lastAttemptDuration, totalDuration
        );
    }

    /**
     * Calculate exponential backoff delay with maximum cap
     */
    private long calculateDelay(RetryConfig config, int attempt) {
        long delay = (long) (config.baseDelayMs() * Math.pow(config.backoffMultiplier(), attempt - 1));
        return Math.min(delay, config.maxDelayMs());
    }
}
