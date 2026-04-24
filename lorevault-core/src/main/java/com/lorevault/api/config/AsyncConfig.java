package com.lorevault.api.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
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
     *
     * Current product stance: we do not support concurrent chapter uploads within the same
     * narrative universe. Keep this executor single-threaded for now so ingestion follow-up
     * work remains serialized. A more targeted concurrency model may still be needed later,
     * but that design work is explicitly deferred.
     */
    @Bean(name = "ingestionTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);           // Intentionally single-threaded while concurrent uploads are unsupported
        executor.setMaxPoolSize(1);            // Preserve serialized follow-up processing within this deferred model
        executor.setQueueCapacity(100);        // Prefer queueing over parallelism until finer-grained concurrency is designed
        executor.setThreadNamePrefix("ingestion-");
        executor.setTaskDecorator(mdcTaskDecorator());
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
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // AI calls might take longer
        executor.initialize();
        return executor;
    }

    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (callerContext != null) {
                        MDC.setContextMap(callerContext);
                    } else {
                        MDC.clear();
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
