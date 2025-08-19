package com.lorevault.api.service.shared;

import com.lorevault.api.configuration.properties.LoreVaultPromptProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for loading and caching AI prompt templates from resources.
 * Provides efficient access to configured PromptTemplate instances with centralized configuration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptLoaderService {

    private final LoreVaultPromptProperties promptProperties;
    private final ResourceLoader resourceLoader;
    
    private final ConcurrentMap<String, PromptTemplate> promptCache = new ConcurrentHashMap<>();

    /**
     * Initialize the service by pre-loading critical prompts.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing PromptLoaderService with base path: {}", promptProperties.basePath());
        
        // Pre-load scene detection prompts
        try {
            getSceneDetectionPass1PromptTemplate();
            getSceneDetectionPass2PromptTemplate();
            getRagAnswerGenerationPromptTemplate();
            log.info("Successfully pre-loaded scene detection pass 1 & 2 and RAG answer generation prompt templates");
        } catch (Exception e) {
            log.error("Failed to pre-load prompt templates: {}", e.getMessage());
        }
    }

    /**
     * Get scene detection pass 1 prompt template from configured location.
     * 
     * @return Configured PromptTemplate for scene detection pass 1
     * @throws RuntimeException if prompt cannot be loaded
     */
    public PromptTemplate getSceneDetectionPass1PromptTemplate() {
        return getPromptTemplate("scene-detection-pass1", promptProperties.getSceneDetectionPass1Path());
    }

    /**
     * Get scene detection pass 2 prompt template from configured location.
     * 
     * @return Configured PromptTemplate for scene detection pass 2
     * @throws RuntimeException if prompt cannot be loaded
     */
    public PromptTemplate getSceneDetectionPass2PromptTemplate() {
        return getPromptTemplate("scene-detection-pass2", promptProperties.getSceneDetectionPass2Path());
    }

    /**
     * Get scene detection prompt template from configured location.
     * 
     * @return Configured PromptTemplate for scene detection
     * @throws RuntimeException if prompt cannot be loaded
     */
    public PromptTemplate getSceneDetectionPromptTemplate() {
        // Use pass 2 as the legacy scene detection (it's the normalized version)
        return getSceneDetectionPass2PromptTemplate();
    }

    /**
     * Get RAG answer generation prompt template from configured location.
     * 
     * @return Configured PromptTemplate for RAG answer generation
     * @throws RuntimeException if prompt cannot be loaded
     */
    public PromptTemplate getRagAnswerGenerationPromptTemplate() {
        return getPromptTemplate("rag-answer-generation", promptProperties.getRagAnswerGenerationPath());
    }

    /**
     * Generic method to load and cache prompt templates with centralized configuration.
     * 
     * @param promptKey Unique key for caching
     * @param resourcePath Full resource path to the prompt file
     * @return Configured PromptTemplate instance
     * @throws RuntimeException if prompt cannot be loaded
     */
    private PromptTemplate getPromptTemplate(String promptKey, String resourcePath) {
        return promptCache.computeIfAbsent(promptKey, key -> loadPromptTemplateFromResource(resourcePath));
    }

    /**
     * Load prompt template from a resource file with centralized configuration.
     * 
     * @param resourcePath The resource path to load from
     * @return Configured PromptTemplate instance
     * @throws RuntimeException if loading fails
     */
    private PromptTemplate loadPromptTemplateFromResource(String resourcePath) {
        try {
            log.debug("Loading prompt template from resource: {}", resourcePath);
            
            Resource resource = resourceLoader.getResource(resourcePath);
            
            if (!resource.exists()) {
                throw new RuntimeException("Prompt resource not found: " + resourcePath);
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            
            if (content.trim().isEmpty()) {
                throw new RuntimeException("Prompt resource is empty: " + resourcePath);
            }
            
            // Create PromptTemplate with default constructor first
            // We'll override the template rendering by manually processing {{}} placeholders
            PromptTemplate template = new PromptTemplate(content) {
                @Override
                public String render(Map<String, Object> variables) {
                    // Custom rendering with {{}} delimiters to avoid XML tag conflicts
                    String result = content;
                    for (Map.Entry<String, Object> entry : variables.entrySet()) {
                        String placeholder = "{{" + entry.getKey() + "}}";
                        String value = entry.getValue() != null ? entry.getValue().toString() : "";
                        result = result.replace(placeholder, value);
                    }
                    return result;
                }
            };
            
            log.debug("Successfully loaded prompt template from {}, length: {} characters", 
                     resourcePath, content.length());
            
            return template;
            
        } catch (IOException e) {
            String errorMsg = "Failed to load prompt template from resource: " + resourcePath;
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Clear the prompt cache. Useful for testing or dynamic prompt updates.
     */
    public void clearCache() {
        promptCache.clear();
        log.info("Prompt template cache cleared");
    }

    /**
     * Get cache statistics for monitoring.
     * 
     * @return Current cache size
     */
    public int getCacheSize() {
        return promptCache.size();
    }
}
