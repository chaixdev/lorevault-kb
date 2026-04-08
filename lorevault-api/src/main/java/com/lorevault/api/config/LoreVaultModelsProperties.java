package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Model-based LLM configuration: embedding, nlp-small, nlp-big.
 * Each model can point to an OpenAI-compatible provider with its own base URL,
 * API key, model, and generation parameters.
 */
@ConfigurationProperties(prefix = "lorevault.ai.models")
@Validated
public record LoreVaultModelsProperties(
    @Valid ModelProperties embedding,
    @Valid ModelProperties nlpSmall,
    @Valid ModelProperties nlpBig
) {
    // Compact constructor to provide defaults for missing sections (test-friendly)
    public LoreVaultModelsProperties {
        if (embedding == null) {
            embedding = new ModelProperties(null, null, null, null, null, null, null, null);
        }
        if (nlpSmall == null) {
            nlpSmall = new ModelProperties(null, null, null, null, null, null, null, null);
        }
        if (nlpBig == null) {
            nlpBig = new ModelProperties(null, null, null, null, null, null, null, null);
        }
    }

    public record ModelProperties(
        String provider,
        String baseUrl,
        String completionsPath,
        String apiKey,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens
    ) {
        // Compact constructor with defaults
        public ModelProperties {
            if (provider == null) provider = "openai-compatible";
            if (completionsPath == null) completionsPath = "/chat/completions";
            if (temperature == null) temperature = 0.3;
            if (topP == null) topP = 1.0;
            if (maxTokens == null) maxTokens = 2048;
        }

        public String safeProvider() { 
            return provider == null ? "openai-compatible" : provider; 
        }
    }
}
