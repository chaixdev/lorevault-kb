package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Slot-based LLM configuration: embedding, small, big.
 * Each slot can point to an OpenAI-compatible provider with its own base URL,
 * API key, model, and common generation parameters.
 */
@ConfigurationProperties(prefix = "lorevault.ai.slots")
@Validated
public record LoreVaultSlotsProperties(
    @Valid SlotProperties embedding,
    @Valid SlotProperties small,
    @Valid SlotProperties big
) {
    public record SlotProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens
    ) {
        public String safeProvider() { return provider == null ? "openai-compatible" : provider; }
    }
}
