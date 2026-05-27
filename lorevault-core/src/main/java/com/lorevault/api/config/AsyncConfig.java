package com.lorevault.api.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig {

    @Value("${lorevault.async.shutdown.wait-for-tasks:true}")
    private boolean waitForTasksToCompleteOnShutdown;

    @Value("${lorevault.async.shutdown.ingestion-await-seconds:60}")
    private int ingestionAwaitTerminationSeconds;

    @Value("${lorevault.async.shutdown.ingestion-lane-await-seconds:60}")
    private int ingestionLaneAwaitTerminationSeconds;

    @Value("${lorevault.async.shutdown.scene-detection-await-seconds:120}")
    private int sceneDetectionAwaitTerminationSeconds;

    /**
     * Custom thread pool for ingestion orchestration and fan-in processing.
     * This ensures that async operations don't block the main HTTP thread pool.
     *
     * Current product stance: we do not support concurrent chapter uploads within the same
     * narrative universe. Keep this executor single-threaded so cross-branch orchestration
     * and completion fan-in remain serialized.
     */
    @Bean(name = "ingestionTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);           // Intentionally single-threaded while concurrent uploads are unsupported
        executor.setMaxPoolSize(1);            // Preserve serialized follow-up processing within this deferred model
        executor.setQueueCapacity(100);        // Prefer queueing over parallelism until finer-grained concurrency is designed
        executor.setThreadNamePrefix("ingestion-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(ingestionAwaitTerminationSeconds);
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
    public Executor ingestionLaneTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingestion-lane-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(ingestionLaneAwaitTerminationSeconds);
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
        executor.setCorePoolSize(1);           // Single-threaded — scene detection is per-chapter,
        executor.setMaxPoolSize(1);            // not parallelizable within a chapter.
        executor.setQueueCapacity(10);         
        executor.setThreadNamePrefix("scene-detection-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(sceneDetectionAwaitTerminationSeconds); // AI calls might take longer
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
