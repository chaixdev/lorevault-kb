package com.lorevault.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;

/**
 * Configuration for Spring Retry functionality.
 * Provides centralized retry configuration for API calls and other operations
 * that may experience transient failures.
 */
@Configuration
@EnableRetry
@RequiredArgsConstructor
public class RetryConfig {

    private final LoreVaultRetryProperties retryProperties;

    /**
     * Creates a RetryTemplate for LLM API calls with exponential backoff.
     * This template is configured with parameters appropriate for external
     * API calls that may experience transient failures.
     * 
     * @return Configured RetryTemplate for LLM operations
     */
    @Bean(name = "llmRetryTemplate")
    public RetryTemplate llmRetryTemplate() {
        var cfg = retryProperties.llmDefaults();
        return buildTemplate(cfg.maxAttempts(), cfg.initialIntervalMs(), cfg.multiplier(), cfg.maxIntervalMs());
    }
    
    /**
     * Creates a RetryTemplate for general database operations with a simple backoff.
     * This template is configured with parameters appropriate for internal
     * operations that may experience transient failures.
     * 
     * @return Configured RetryTemplate for database operations
     */
    @Bean(name = "dbRetryTemplate")
    public RetryTemplate dbRetryTemplate() {
        var cfg = retryProperties.dbDefaults();
        return buildTemplate(cfg.maxAttempts(), cfg.initialIntervalMs(), cfg.multiplier(), cfg.maxIntervalMs());
    }
    
    /**
     * Creates a default RetryTemplate for Spring AI's auto-configured components.
     * This is necessary because Spring AI requires a RetryTemplate bean and will
     * fail if it finds multiple named beans.
     * 
     * @return Default RetryTemplate for Spring AI components
     */
    @Bean
    public RetryTemplate retryTemplate() {
        var cfg = retryProperties.springAiDefaults();
        return buildTemplate(cfg.maxAttempts(), cfg.initialIntervalMs(), cfg.multiplier(), cfg.maxIntervalMs());
    }

    private RetryTemplate buildTemplate(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {
        RetryTemplate retryTemplate = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            maxAttempts,
            Collections.singletonMap(Exception.class, true)
        );

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialIntervalMs);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxIntervalMs);

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
