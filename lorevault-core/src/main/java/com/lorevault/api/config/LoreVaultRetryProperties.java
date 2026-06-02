package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Configuration properties for Spring Retry templates.
 * <p>
 * Provides tunable retry behaviour for LLM API calls, database operations,
 * and Spring AI's default retry template — each with independent attempts,
 * back-off, and max interval settings.
 */
@ConfigurationProperties(prefix = "lorevault.retry")
@Validated
public record LoreVaultRetryProperties(
    @Valid Template llm,
    @Valid Template db,
    @Valid Template springAi
) {
    public LoreVaultRetryProperties {
        if (llm == null)        llm = new Template(null, null, null, null);
        if (db == null)         db  = new Template(null, null, null, null);
        if (springAi == null)   springAi = new Template(null, null, null, null);
    }

    public record Template(
        Integer maxAttempts,
        Long    initialIntervalMs,
        Double  multiplier,
        Long    maxIntervalMs
    ) {
        /** Defaults are set by each consumer — this record only carries the raw values. */
    }

    /** Returns the effective LLM template with sensible defaults if unconfigured. */
    public Effective llmDefaults() {
        return resolve(llm, 3, 2000L, 2.0, 30000L);
    }

    /** Returns the effective DB template with sensible defaults if unconfigured. */
    public Effective dbDefaults() {
        return resolve(db, 3, 100L, 2.0, 1000L);
    }

    /** Returns the effective Spring-AI template with sensible defaults if unconfigured. */
    public Effective springAiDefaults() {
        return resolve(springAi, 3, 1000L, 2.0, 15000L);
    }

    private Effective resolve(Template t, int dfltAttempts, long dfltInitial, double dfltMult, long dfltMax) {
        return new Effective(
            t.maxAttempts()       != null ? t.maxAttempts()       : dfltAttempts,
            t.initialIntervalMs() != null ? t.initialIntervalMs() : dfltInitial,
            t.multiplier()        != null ? t.multiplier()        : dfltMult,
            t.maxIntervalMs()     != null ? t.maxIntervalMs()     : dfltMax
        );
    }

    /** Resolved view with concrete non-null values. */
    public record Effective(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {}
}
