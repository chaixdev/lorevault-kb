package com.lorevault.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Custom thread pool for ingestion processing.
     * This ensures that async operations don't block the main HTTP thread pool.
     */
    @Bean(name = "ingestionTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);           // Minimum threads
        executor.setMaxPoolSize(10);           // Maximum threads
        executor.setQueueCapacity(25);         // Queue size before creating new threads
        executor.setThreadNamePrefix("ingestion-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Custom thread pool for scene detection.
     * Separate pool to isolate AI processing from other tasks.
     */
    @Bean(name = "sceneDetectionTaskExecutor")
    public Executor sceneDetectionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);           // AI calls are typically sequential
        executor.setMaxPoolSize(3);            // Limited concurrent AI calls
        executor.setQueueCapacity(10);         
        executor.setThreadNamePrefix("scene-detection-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // AI calls might take longer
        executor.initialize();
        return executor;
    }
}
