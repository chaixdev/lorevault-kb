package com.lorevault.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AI prompts.
 * Allows customization of prompt templates and their locations.
 */
@Component
@ConfigurationProperties(prefix = "lorevault.ai.prompts")
@Data
public class PromptProperties {

    /**
     * Base path for prompt template files.
     * Default: classpath:prompts
     */
    private String basePath = "classpath:prompts";

    /**
     * Filename for scene detection prompt template.
     * Default: scene-detection.txt
     */
    private String sceneDetection = "scene-detection.txt";

    /**
     * Get the full resource path for scene detection prompt.
     */
    public String getSceneDetectionPath() {
        return basePath + "/" + sceneDetection;
    }
}
