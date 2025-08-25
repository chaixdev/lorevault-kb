package com.lorevault.api.application.port;

import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * Port for loading AI prompt templates.
 * Abstracts the prompt loading mechanism from the application services.
 * Implemented by infrastructure adapters.
 */
public interface PromptRepositoryPort {

    /**
     * Get a prompt template by logical name.
     * 
     * @param name logical prompt name (e.g., "scene-detection-pass1")
     * @return the loaded PromptTemplate
     * @throws RuntimeException if prompt cannot be loaded
     */
    PromptTemplate get(String name);

    /**
     * Clear the internal cache. Useful for testing or dynamic updates.
     */
    void clearCache();

    /**
     * Get cache statistics for monitoring.
     * 
     * @return current cache size
     */
    int getCacheSize();
}