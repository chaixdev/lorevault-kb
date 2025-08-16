package com.lorevault.api.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for AI prompts under lorevault.ai.prompts.
 * Maps to the prompt configuration structure in application.yml.
 */
@ConfigurationProperties(prefix = "lorevault.ai.prompts")
@Validated
public record LoreVaultPromptProperties(
    String basePath,
    PromptConfig sceneDetection,
    PromptConfig sceneDetectionPass1,
    PromptConfig sceneDetectionPass2,
    PromptConfig entityExtraction
) {
    
    public LoreVaultPromptProperties {
        // Apply defaults
        if (basePath == null) {
            basePath = "classpath:prompts";
        }
    }
    
    /**
     * Configuration for a specific prompt task.
     */
    public record PromptConfig(
        String systemPrompt,
        String model
    ) {}
    
    /**
     * Get the full resource path for a prompt file.
     */
    public String getPromptPath(String promptFile) {
        return basePath + "/" + promptFile;
    }
    
    /**
     * Get scene detection pass 1 prompt path.
     */
    public String getSceneDetectionPass1Path() {
        return sceneDetectionPass1 != null && sceneDetectionPass1.systemPrompt() != null 
            ? getPromptPath(sceneDetectionPass1.systemPrompt()) 
            : getPromptPath("scene-detection-pass1.txt");
    }
    
    /**
     * Get scene detection pass 2 prompt path.
     */
    public String getSceneDetectionPass2Path() {
        return sceneDetectionPass2 != null && sceneDetectionPass2.systemPrompt() != null 
            ? getPromptPath(sceneDetectionPass2.systemPrompt()) 
            : getPromptPath("scene-detection-pass2.txt");
    }
    
    /**
     * Get scene detection pass 1 model.
     */
    public String getSceneDetectionPass1Model() {
        return sceneDetectionPass1 != null && sceneDetectionPass1.model() != null 
            ? sceneDetectionPass1.model() 
            : "nlp-small";
    }
    
    /**
     * Get scene detection pass 2 model.
     */
    public String getSceneDetectionPass2Model() {
        return sceneDetectionPass2 != null && sceneDetectionPass2.model() != null 
            ? sceneDetectionPass2.model() 
            : "nlp-small";
    }
}
