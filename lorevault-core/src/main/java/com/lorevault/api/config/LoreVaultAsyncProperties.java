package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Configuration properties for LoreVault's async executor pools.
 * <p>
 * Provides shutdown behavior and executor lifecycle settings for the three
 * task executors: ingestion fan-in, ingestion lane fan-out, and scene detection.
 */
@ConfigurationProperties(prefix = "lorevault.async")
@Validated
public record LoreVaultAsyncProperties(
    @Valid Shutdown shutdown
) {
    public LoreVaultAsyncProperties {
        if (shutdown == null) {
            shutdown = new Shutdown(null, null, null, null);
        }
    }

    public record Shutdown(
        Boolean waitForTasks,
        Integer ingestionAwaitSeconds,
        Integer ingestionLaneAwaitSeconds,
        Integer sceneDetectionAwaitSeconds
    ) {
        public Shutdown {
            if (waitForTasks == null) waitForTasks = true;
            if (ingestionAwaitSeconds == null) ingestionAwaitSeconds = 60;
            if (ingestionLaneAwaitSeconds == null) ingestionLaneAwaitSeconds = 60;
            if (sceneDetectionAwaitSeconds == null) sceneDetectionAwaitSeconds = 120;
        }
    }
}
