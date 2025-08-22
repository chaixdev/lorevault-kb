package com.lorevault.api.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for persisting LLM request/response data during ingestion.
 */
@ConfigurationProperties(prefix = "lorevault.ai.llm-logging")
@Validated
public record LoreVaultLlmLoggingProperties(
    Boolean enabled,
    Boolean persistBodiesEnabled,
    Integer maxBodyChars,
    Boolean storeRenderedPrompt
) {
    public LoreVaultLlmLoggingProperties {
        if (enabled == null) enabled = true;
        if (persistBodiesEnabled == null) persistBodiesEnabled = true;
        // -1 means no truncation in dev by default
        if (maxBodyChars == null) maxBodyChars = -1;
        if (storeRenderedPrompt == null) storeRenderedPrompt = true;
    }
}
