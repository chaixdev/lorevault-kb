package com.lorevault.api.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.springframework.core.task.TaskExecutor;

import java.util.Map;

/**
 * Configuration for asynchronous processing
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
@RequiredArgsConstructor
public class AsyncConfig {

    private final LoreVaultAsyncProperties asyncProperties;

    /**
     * Custom thread pool for ingestion orchestration and fan-in processing.
     * This ensures that async operations don't block the main HTTP thread pool.
     *
     * Current product stance: we do not support concurrent chapter uploads within the same
     * narrative universe. Keep this executor single-threaded so cross-branch orchestration
     * and completion fan-in remain serialized.
     */
    @Bean(name = "ingestionTaskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);           // Intentionally single-threaded while concurrent uploads are unsupported
        executor.setMaxPoolSize(1);            // Preserve serialized follow-up processing within this deferred model
        executor.setQueueCapacity(100);        // Prefer queueing over parallelism until finer-grained concurrency is designed
        executor.setThreadNamePrefix("ingestion-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(asyncProperties.shutdown().waitForTasks());
        executor.setAwaitTerminationSeconds(asyncProperties.shutdown().ingestionAwaitSeconds());
        executor.initialize();
        return executor;
    }

    /**
     * Bounded pool for independent branches within a single chapter ingestion.
     *
     * <p>Scene detection remains isolated on its own executor.  Completion fan-in remains on the
     * single-threaded {@code ingestionTaskExecutor}.  This pool is for branch work between those
     * boundaries: chunking/embedding, chapter-level entity resolution, book-level lane reduction,
     * and event embedding/candidate generation.</p>
     */
    @Bean(name = "ingestionLaneTaskExecutor")
    public TaskExecutor ingestionLaneTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingestion-lane-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(asyncProperties.shutdown().waitForTasks());
        executor.setAwaitTerminationSeconds(asyncProperties.shutdown().ingestionLaneAwaitSeconds());
        executor.initialize();
        return executor;
    }

    /**
     * Custom thread pool for scene detection.
     * Separate pool to isolate AI processing from other tasks.
     */
    @Bean(name = "sceneDetectionTaskExecutor")
    public TaskExecutor sceneDetectionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);           // Single-threaded — scene detection is per-chapter,
        executor.setMaxPoolSize(1);            // not parallelizable within a chapter.
        executor.setQueueCapacity(10);         
        executor.setThreadNamePrefix("scene-detection-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(asyncProperties.shutdown().waitForTasks());
        executor.setAwaitTerminationSeconds(asyncProperties.shutdown().sceneDetectionAwaitSeconds()); // AI calls might take longer
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
