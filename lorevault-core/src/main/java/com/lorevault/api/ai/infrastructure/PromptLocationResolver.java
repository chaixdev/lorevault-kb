package com.lorevault.api.ai.infrastructure;

import com.lorevault.api.config.LoreVaultPromptProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves logical prompt names to resource paths using configuration.
 * Centralizes the mapping between prompt names and their file locations.
 */
@Component
public class PromptLocationResolver {

    private final LoreVaultPromptProperties promptProperties;

    public PromptLocationResolver(LoreVaultPromptProperties promptProperties) {
        this.promptProperties = promptProperties;
    }

    /**
     * Resolve a logical prompt name to its resource path.
     * 
     * @param name the prompt name enum
     * @return the full resource path
     */
    public String resolve(PromptName name) {
        return switch (name) {
            case CHAPTER_SEGMENTATION -> getChapterSegmentationPath();
            case SCENE_ANALYSIS -> getSceneAnalysisPath();
            case SCENE_ANALYSIS_USER -> getSceneAnalysisUserPath();
            case RAG_ANSWER_GENERATION -> getRagAnswerGenerationPath();
            case EVENT_COREF_SYSTEM -> promptProperties.getEventCorefSystemPath();
            case EVENT_COREF_USER -> promptProperties.getPromptPath("event-coref-usertemplate.st");
            case EVENT_MERGE_SYSTEM -> promptProperties.getEventMergeSystemPath();
            case EVENT_MERGE_USER -> promptProperties.getPromptPath("event-merge-user.st");
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
