package com.lorevault.api.infrastructure.prompt;

import com.lorevault.api.configuration.properties.LoreVaultPromptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves logical prompt names to resource paths using configuration.
 * Centralizes the mapping between prompt names and their file locations.
 */
@Component
@RequiredArgsConstructor
public class PromptLocationResolver {

    private final LoreVaultPromptProperties promptProperties;

    /**
     * Resolve a logical prompt name to its resource path.
     * 
     * @param logicalName the logical name (e.g., "scene-detection-pass1")
     * @return the full resource path
     * @throws IllegalArgumentException if prompt name is not recognized
     */
    public String resolve(String logicalName) {
        return switch (logicalName) {
            case "scene-detection-pass1" -> getSceneDetectionPass1Path();
            case "scene-detection-pass2" -> getSceneDetectionPass2Path();
            case "scene-detection" -> getSceneDetectionPass2Path(); // legacy alias
            case "scene-detection-pass2-user" -> getSceneDetectionPass2UserPath();
            case "rag-answer-generation" -> getRagAnswerGenerationPath();
            default -> throw new IllegalArgumentException("Unknown prompt name: " + logicalName);
        };
    }

    private String getSceneDetectionPass1Path() {
        return promptProperties.getSceneDetectionPass1Path();
    }

    private String getSceneDetectionPass2Path() {
        return promptProperties.getSceneDetectionPass2Path();
    }

    private String getSceneDetectionPass2UserPath() {
        // Use .st extension to ensure ST4 template renderer is selected
        return promptProperties.getPromptPath("scene-detection-pass2-usertemplate.st");
    }

    private String getRagAnswerGenerationPath() {
        return promptProperties.getRagAnswerGenerationPath();
    }
}
