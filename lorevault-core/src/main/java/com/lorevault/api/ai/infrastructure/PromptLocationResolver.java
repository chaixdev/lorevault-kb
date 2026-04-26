package com.lorevault.api.ai.infrastructure;

import com.lorevault.api.config.LoreVaultPromptProperties;
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
     * @param logicalName the logical name (e.g., "chapter-segmentation")
     * @return the full resource path
     * @throws IllegalArgumentException if prompt name is not recognized
     */
    public String resolve(String logicalName) {
        return switch (logicalName) {
            case "chapter-segmentation" -> getChapterSegmentationPath();
            case "scene-analysis" -> getSceneAnalysisPath();
            case "scene-analysis-user" -> getSceneAnalysisUserPath();
            case "rag-answer-generation" -> getRagAnswerGenerationPath();
            case "event-coref-system" -> promptProperties.getPromptPath("event-coref-system.st");
            case "event-coref-user" -> promptProperties.getPromptPath("event-coref-usertemplate.st");
            default -> throw new IllegalArgumentException("Unknown prompt name: " + logicalName);
        };
    }

    private String getChapterSegmentationPath() {
        return promptProperties.getChapterSegmentationPath();
    }

    private String getSceneAnalysisPath() {
        return promptProperties.getSceneAnalysisPath();
    }

    private String getSceneAnalysisUserPath() {
        return promptProperties.getPromptPath("scene-analysis-usertemplate.st");
    }

    private String getRagAnswerGenerationPath() {
        return promptProperties.getRagAnswerGenerationPath();
    }
}
