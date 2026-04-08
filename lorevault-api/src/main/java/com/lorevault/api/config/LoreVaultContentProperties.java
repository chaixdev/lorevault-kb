package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for content processing operations.
 * Centralizes configuration for text chunking, processing, and content management.
 */
@ConfigurationProperties(prefix = "lorevault.content")
@Validated
public record LoreVaultContentProperties(
    @Valid @NotNull ChunkingProperties chunking
) {
    
    /**
     * Configuration for text chunking operations.
     */
    public record ChunkingProperties(
        Integer targetSize,
        Integer overlapSize,
        String strategy,
        SentenceSplitterProperties sentenceSplitter
    ) {
        public ChunkingProperties {
            // Apply defaults
            if (targetSize == null) {
                targetSize = 1000;
            }
            if (overlapSize == null) {
                overlapSize = 200;
            }
            if (strategy == null) {
                strategy = "sentence-aware";
            }
        }
    }
    
    /**
     * Configuration for sentence splitting within chunks.
     */
    public record SentenceSplitterProperties(
        Integer maxSentenceLength,
        Boolean preserveDialogue
    ) {
        public SentenceSplitterProperties {
            // Apply defaults
            if (maxSentenceLength == null) {
                maxSentenceLength = 300;
            }
            if (preserveDialogue == null) {
                preserveDialogue = true;
            }
        }
    }
}
