package com.lorevault.api.service;

import com.lorevault.api.config.PromptProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for loading and caching AI prompt templates from resources.
 * Provides efficient access to prompt templates with fallback handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptLoaderService {

    private final PromptProperties promptProperties;
    private final ResourceLoader resourceLoader;
    
    private final ConcurrentMap<String, String> promptCache = new ConcurrentHashMap<>();

    /**
     * Initialize the service by pre-loading critical prompts.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing PromptLoaderService with base path: {}", promptProperties.getBasePath());
        
        // Pre-load scene detection prompt
        try {
            getSceneDetectionPrompt();
            log.info("Successfully pre-loaded scene detection prompt");
        } catch (Exception e) {
            log.error("Failed to pre-load scene detection prompt: {}", e.getMessage());
        }
    }

    /**
     * Get the scene detection prompt template.
     * 
     * @return The scene detection prompt template
     * @throws RuntimeException if prompt cannot be loaded
     */
    public String getSceneDetectionPrompt() {
        return getPrompt("scene-detection", promptProperties.getSceneDetectionPath());
    }

    /**
     * Generic method to load and cache prompt templates.
     * 
     * @param promptKey Unique key for caching
     * @param resourcePath Full resource path to the prompt file
     * @return The prompt template content
     * @throws RuntimeException if prompt cannot be loaded
     */
    private String getPrompt(String promptKey, String resourcePath) {
        return promptCache.computeIfAbsent(promptKey, key -> loadPromptFromResource(resourcePath));
    }

    /**
     * Load prompt content from a resource file.
     * 
     * @param resourcePath The resource path to load from
     * @return The prompt content
     * @throws RuntimeException if loading fails
     */
    private String loadPromptFromResource(String resourcePath) {
        try {
            log.debug("Loading prompt from resource: {}", resourcePath);
            
            Resource resource = resourceLoader.getResource(resourcePath);
            
            if (!resource.exists()) {
                throw new RuntimeException("Prompt resource not found: " + resourcePath);
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("Prompt resource is empty: " + resourcePath);
            }
            
            log.debug("Successfully loaded prompt from {}, length: {} characters", 
                     resourcePath, content.length());
            
            return content;
            
        } catch (IOException e) {
            String errorMsg = "Failed to load prompt from resource: " + resourcePath;
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Clear the prompt cache. Useful for testing or dynamic prompt updates.
     */
    public void clearCache() {
        promptCache.clear();
        log.info("Prompt cache cleared");
    }

    /**
     * Get cache statistics for monitoring.
     */
    public int getCacheSize() {
        return promptCache.size();
    }
}
