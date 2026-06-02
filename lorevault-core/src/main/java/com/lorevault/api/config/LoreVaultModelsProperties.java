package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Model-based LLM configuration: embedding, nlp-small, nlp-big.
 * Each model can point to an OpenAI-compatible provider with its own base URL,
 * API key, and model name.
 *
 * <p>Generation parameters (temperature, topP, completionsPath) are
 * code-design constants in {@link SpringAiConfig} — they are not tunable
 * at runtime because changing them silently degrades prompt quality.
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
        if (embedding == null) embedding = new ModelProperties(null, null, null, null, null);
        if (nlpSmall == null)  nlpSmall  = new ModelProperties(null, null, null, null, null);
        if (nlpBig == null)    nlpBig    = new ModelProperties(null, null, null, null, null);
    }

    public record ModelProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Integer maxContextTokens
    ) {
        public ModelProperties {
            if (provider == null) provider = "openai-compatible";
            if (maxContextTokens == null) maxContextTokens = 128000;
        }

        public String safeProvider() {
            return provider == null ? "openai-compatible" : provider;
        }
    }
}
