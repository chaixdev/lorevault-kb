package com.lorevault.api.config;

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
public class RetryConfig {

    /**
     * Creates a RetryTemplate for LLM API calls with exponential backoff.
     * This template is configured with parameters appropriate for external
     * API calls that may experience transient failures.
     * 
     * @return Configured RetryTemplate for LLM operations
     */
    @Bean(name = "llmRetryTemplate")
    public RetryTemplate llmRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Set the retry policy (how many times to retry)
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            3, // Max attempts
            Collections.singletonMap(Exception.class, true) // Retry on all exceptions
        );
        
        // Set the backoff policy (how long to wait between retries)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000); // 2 seconds initial delay
        backOffPolicy.setMultiplier(2.0);       // Double the wait time for each retry
        backOffPolicy.setMaxInterval(30000);    // Maximum 30 second delay
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
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
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Set the retry policy (how many times to retry)
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            3, // Max attempts
            Collections.singletonMap(Exception.class, true) // Retry on all exceptions
        );
        
        // Set the backoff policy (how long to wait between retries)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(100);  // 100ms initial delay
        backOffPolicy.setMultiplier(2.0);       // Double the wait time for each retry
        backOffPolicy.setMaxInterval(1000);     // Maximum 1 second delay
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
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
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Set the retry policy (how many times to retry)
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            3, // Max attempts
            Collections.singletonMap(Exception.class, true) // Retry on all exceptions
        );
        
        // Set the backoff policy (how long to wait between retries)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second initial delay
        backOffPolicy.setMultiplier(2.0);       // Double the wait time for each retry
        backOffPolicy.setMaxInterval(15000);    // Maximum 15 second delay
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }
}
